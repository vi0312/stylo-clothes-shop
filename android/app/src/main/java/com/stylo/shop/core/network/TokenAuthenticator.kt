package com.stylo.shop.core.network

import com.stylo.shop.core.storage.SessionManager
import com.stylo.shop.core.storage.TokenPair
import com.stylo.shop.feature.auth.data.AuthRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

/**
 * Refreshes an expired access token once per request. The refresh call itself travels on the
 * bare client (see `@BareClient`), so a rejected refresh cannot re-enter this authenticator.
 */
class TokenAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
    private val refresher: TokenRefresher,
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val failedRequest = response.request
        val authorization = failedRequest.header("Authorization") ?: return null

        // OkHttp calls authenticate again after the retry; stop at the second attempt.
        if (responseCount(response) >= 2) return null

        val failedToken = authorization.removePrefix("Bearer ").trim()

        return runBlocking {
            refreshMutex.withLock {
                val current = sessionManager.accessToken
                if (current != null && current != failedToken) {
                    return@withLock failedRequest.withToken(current)
                }

                val refreshToken = sessionManager.refreshToken ?: run {
                    sessionManager.onRefreshFailed()
                    return@withLock null
                }

                when (val result = refresher.refresh(refreshToken)) {
                    is ApiResult.Success -> {
                        sessionManager.onTokens(result.data)
                        failedRequest.withToken(result.data.accessToken)
                    }
                    is ApiResult.Failure -> {
                        sessionManager.onRefreshFailed()
                        null
                    }
                }
            }
        }
    }

    private fun Request.withToken(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()

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

interface TokenRefresher {
    suspend fun refresh(refreshToken: String): ApiResult<TokenPair>
}

class HttpTokenRefresher @Inject constructor(
    private val authRepository: AuthRepository,
) : TokenRefresher {

    override suspend fun refresh(refreshToken: String): ApiResult<TokenPair> =
        authRepository.refresh(refreshToken)
}
