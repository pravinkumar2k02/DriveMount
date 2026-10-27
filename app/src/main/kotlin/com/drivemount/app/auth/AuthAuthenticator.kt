package com.drivemount.app.auth

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] that handles HTTP 401 Unauthorized responses by refreshing the OAuth token.
 */
@Singleton
class AuthAuthenticator @Inject constructor(
    private val authInterceptorProvider: Provider<AuthInterceptor>,
    private val tokenStore: TokenStore,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite loops: only retry once on 401
        if (responseCount(response) >= 3) {
            Timber.w("Too many 401 retry attempts, giving up.")
            return null
        }

        // Identify account from original request header or tag
        val accountId = response.request.header("X-Retry-Account-Id")
            ?: return null

        Timber.d("Received 401 for account %s, attempting re-authentication...", accountId)

        val newAccessToken = authInterceptorProvider.get().performTokenRefresh(accountId) ?: run {
            Timber.e("Failed to refresh token after 401 for %s", accountId)
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccessToken")
            .header("X-Retry-Account-Id", accountId)
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

