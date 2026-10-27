package com.drivemount.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.drivemount.app.BuildConfig
import com.drivemount.app.data.api.DriveApiClient
import com.drivemount.app.data.api.DriveApiConstants
import com.drivemount.app.data.cache.BlockCache
import com.drivemount.app.data.db.AccountDao
import com.drivemount.app.data.db.AccountEntity
import com.drivemount.app.data.db.DriveFileDao
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user authentication and Google Drive OAuth authorization.
 *
 * Implements Google Sign-In using Android Credential Manager for ID token retrieval,
 * and Google Identity [AuthorizationClient] to acquire offline Drive scope authorization.
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
    private val accountDao: AccountDao,
    private val driveFileDao: DriveFileDao,
    private val blockCache: BlockCache,
    private val json: Json,
) {
    private val credentialManager = CredentialManager.create(context)
    private val rawHttpClient = OkHttpClient.Builder().build()

    /**
     * Observes all signed-in Google accounts.
     */
    fun getSignedInAccounts(): Flow<List<AccountEntity>> = accountDao.getAllAccounts()

    /**
     * Initiates the Google Sign-In and Drive scope authorization flow.
     *
     * @param activityContext Context of the calling Activity (required for UI dialogs).
     * @return The authorized [AccountEntity].
     */
    suspend fun signIn(activityContext: Context): AccountEntity = withContext(Dispatchers.IO) {
        val serverClientId = BuildConfig.WEB_CLIENT_ID.ifBlank {
            "768541239870-mockclientid.apps.googleusercontent.com"
        }

        // 1. Get Google ID Token via Credential Manager
        val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialResponse = try {
            credentialManager.getCredential(activityContext, request)
        } catch (e: GetCredentialCancellationException) {
            throw IOException("Sign-in cancelled by user", e)
        } catch (e: GetCredentialException) {
            Timber.e(e, "Credential Manager error")
            throw IOException("Credential retrieval failed: ${e.message}", e)
        }

        val credential = credentialResponse.credential
        val (email, displayName, photoUrl) = when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val idTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    Triple(
                        idTokenCredential.id,
                        idTokenCredential.displayName ?: idTokenCredential.id,
                        idTokenCredential.profilePictureUri?.toString()
                    )
                } else {
                    throw IllegalStateException("Unexpected credential type: ${credential.type}")
                }
            }
            else -> throw IllegalStateException("Unsupported credential class: ${credential::class.java}")
        }

        Timber.i("Google Sign-In successful for %s. Requesting Drive authorization...", email)

        // 2. Request Drive Scope authorization for offline access (refresh token)
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(DriveApiConstants.SCOPE_DRIVE),
                    Scope(DriveApiConstants.SCOPE_EMAIL),
                    Scope(DriveApiConstants.SCOPE_PROFILE)
                )
            )
            .requestOfflineAccess(serverClientId, false)
            .build()

        val authorizationResult = try {
            Identity.getAuthorizationClient(activityContext)
                .authorize(authRequest)
                .await()
        } catch (e: Exception) {
            Timber.e(e, "AuthorizationClient failed to authorize Drive scope")
            throw IOException("Drive authorization failed: ${e.message}", e)
        }

        val serverAuthCode = authorizationResult.serverAuthCode
        val (accessToken, refreshToken, expiresIn) = if (!serverAuthCode.isNullOrBlank()) {
            exchangeAuthCode(serverAuthCode, serverClientId)
        } else {
            // If access token was returned directly
            val token = authorizationResult.accessToken ?: ""
            Triple(token, "", 3600L)
        }

        val expiryEpoch = System.currentTimeMillis() + (expiresIn * 1000L)

        // Save tokens securely
        if (refreshToken.isNotBlank()) {
            tokenStore.storeRefreshToken(email, refreshToken)
        }
        tokenStore.storeAccessToken(email, accessToken, expiryEpoch)

        val accountEntity = AccountEntity(
            accountId = email,
            displayName = displayName,
            photoUrl = photoUrl,
            refreshToken = refreshToken,
            accessToken = accessToken,
            tokenExpiry = expiryEpoch,
            changesPageToken = null,
            storageLimit = 15L * 1024L * 1024L * 1024L, // Default 15 GB initial
            storageUsed = 0L,
            lastSyncedAt = System.currentTimeMillis()
        )

        accountDao.insert(accountEntity)
        Timber.i("Account %s successfully mounted and registered in database.", email)

        accountEntity
    }

    /**
     * Exchanges an authorization code for OAuth access and refresh tokens.
     */
    suspend fun exchangeAuthCode(authCode: String, clientId: String): Triple<String, String, Long> = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", authCode)
            .build()

        val request = Request.Builder()
            .url(DriveApiConstants.TOKEN_URL)
            .post(formBody)
            .build()

        rawHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Timber.e("Auth code exchange failed: %s", errorBody)
                throw IOException("Token exchange failed with HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw IOException("Empty token response")
            val obj = json.parseToJsonElement(body).jsonObject

            val accessToken = obj["access_token"]?.jsonPrimitive?.content ?: ""
            val refreshToken = obj["refresh_token"]?.jsonPrimitive?.content ?: ""
            val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L

            Triple(accessToken, refreshToken, expiresIn)
        }
    }

    /**
     * Removes an account from local storage and deletes all cached files for it.
     */
    suspend fun removeAccount(accountId: String) = withContext(Dispatchers.IO) {
        Timber.i("Removing account %s and associated cache...", accountId)
        tokenStore.removeRefreshToken(accountId)
        driveFileDao.deleteByAccountId(accountId)
        accountDao.delete(accountId)
        // Evict cached blocks for this account
        blockCache.evictFile(accountId, "")
    }
}

