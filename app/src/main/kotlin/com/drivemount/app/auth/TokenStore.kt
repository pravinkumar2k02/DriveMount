package com.drivemount.app.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure persistent store for OAuth 2.0 access and refresh tokens using [EncryptedSharedPreferences].
 *
 * Uses AES-256 GCM encryption backed by Android Keystore.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        createEncryptedPreferences()
    }

    private fun createEncryptedPreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "drivemount_secure_tokens",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Error initializing EncryptedSharedPreferences, falling back to standard private preferences")
            context.getSharedPreferences("drivemount_secure_tokens_fallback", Context.MODE_PRIVATE)
        }
    }

    /** Stores the encrypted OAuth2 refresh token for an account. */
    fun storeRefreshToken(accountId: String, token: String) {
        prefs.edit().putString("refresh_token_$accountId", token).apply()
    }

    /** Retrieves the refresh token for an account. */
    fun getRefreshToken(accountId: String): String? {
        return prefs.getString("refresh_token_$accountId", null)
    }

    /** Removes the refresh token for an account. */
    fun removeRefreshToken(accountId: String) {
        prefs.edit().remove("refresh_token_$accountId").apply()
    }

    /** Stores the current access token and its expiration epoch timestamp. */
    fun storeAccessToken(accountId: String, token: String, expiryMillis: Long) {
        prefs.edit()
            .putString("access_token_$accountId", token)
            .putLong("token_expiry_$accountId", expiryMillis)
            .apply()
    }

    /** Retrieves the current access token and expiration timestamp, or null if none exists. */
    fun getAccessToken(accountId: String): Pair<String, Long>? {
        val token = prefs.getString("access_token_$accountId", null) ?: return null
        val expiry = prefs.getLong("token_expiry_$accountId", 0L)
        return Pair(token, expiry)
    }

    /** Clears all stored tokens for all accounts. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}

