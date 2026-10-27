package com.drivemount.app.auth

import com.drivemount.app.BuildConfig
import com.drivemount.app.data.api.DriveApiConstants
import com.drivemount.app.data.db.AccountDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp [Interceptor] that manages OAuth2 Bearer token injection and proactive refresh.
 *
 * Reads the custom [DriveApiConstants.HEADER_ACCOUNT_ID] header from the incoming request,
 * strips it, and attaches an `Authorization: Bearer <token>` header for that account.
 * If the current access token is expired or expiring within 60 seconds, it is refreshed
 * synchronously before sending the request.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val accountDao: AccountDao,
    private val json: Json,
) : Interceptor {

    // Dedicated unauthenticated client for token refresh to avoid interceptor recursion
    private val refreshClient = OkHttpClient.Builder().build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val accountId = originalRequest.header(DriveApiConstants.HEADER_ACCOUNT_ID)

        val requestBuilder = originalRequest.newBuilder()
            .removeHeader(DriveApiConstants.HEADER_ACCOUNT_ID)

        if (accountId.isNullOrBlank()) {
            return chain.proceed(requestBuilder.build())
        }

        // Check if token exists and is valid
        var (accessToken, expiry) = tokenStore.getAccessToken(accountId) ?: Pair("", 0L)
        val now = System.currentTimeMillis()
        val isExpiringSoon = (expiry - now) < 60_000L

        if (accessToken.isBlank() || isExpiringSoon) {
            Timber.d("Access token for %s is expired or expiring soon. Refreshing...", accountId)
            val refreshedToken = performTokenRefresh(accountId)
            if (refreshedToken != null) {
                accessToken = refreshedToken
            }
        }

        if (accessToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $accessToken")
        }

        return chain.proceed(requestBuilder.build())
    }

    /**
     * Refreshes the OAuth 2.0 access token synchronously using the refresh token.
     */
    @Synchronized
    fun performTokenRefresh(accountId: String): String? {
        val refreshToken = tokenStore.getRefreshToken(accountId)
        if (refreshToken.isNullOrBlank()) {
            Timber.w("No refresh token available for account %s", accountId)
            return null
        }

        val clientId = BuildConfig.WEB_CLIENT_ID
        val formBodyBuilder = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)

        val refreshRequest = Request.Builder()
            .url(DriveApiConstants.TOKEN_URL)
            .post(formBodyBuilder.build())
            .build()

        return try {
            refreshClient.newCall(refreshRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("Token refresh failed with HTTP %d for %s", response.code, accountId)
                    return null
                }

                val bodyString = response.body?.string() ?: return null
                val jsonObject = json.parseToJsonElement(bodyString).jsonObject
                val newAccessToken = jsonObject["access_token"]?.jsonPrimitive?.content ?: return null
                val expiresInSeconds = jsonObject["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
                val newExpiry = System.currentTimeMillis() + (expiresInSeconds * 1000L)

                tokenStore.storeAccessToken(accountId, newAccessToken, newExpiry)
                accountDao.updateTokensSync(accountId, newAccessToken, newExpiry)
                Timber.i("Successfully refreshed access token for %s", accountId)
                newAccessToken
            }
        } catch (e: IOException) {
            Timber.e(e, "Network error during token refresh for %s", accountId)
            null
        }
    }
}

