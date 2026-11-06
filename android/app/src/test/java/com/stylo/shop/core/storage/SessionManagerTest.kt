package com.stylo.shop.core.storage

import com.stylo.shop.FakeAuthRepository
import com.stylo.shop.FakeTokenStore
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.testTokens
import com.stylo.shop.testUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `stored token confirmed by me restores the session`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeTokenStore(testTokens)
        val repository = FakeAuthRepository()
        val sessionManager = SessionManager(store, repository, this)

        sessionManager.bootstrap()
        advanceUntilIdle()

        assertEquals(SessionState.Authenticated(testUser), sessionManager.state.value)
        assertEquals(testTokens, store.tokens)
    }

    @Test
    fun `expired access token refreshes once and retries me`() = runTest(mainDispatcherRule.dispatcher) {
        val expired = TokenPair(accessToken = "expired", refreshToken = "refresh-1")
        val refreshed = TokenPair(accessToken = "fresh", refreshToken = "refresh-2")
        val store = FakeTokenStore(expired)
        val repository = FakeAuthRepository(
            refreshResult = { ApiResult.Success(refreshed) },
            currentUserResult = { token ->
                if (token == "expired") {
                    ApiResult.Failure(ApiError(ApiErrorKind.HTTP, code = "UNAUTHORIZED", httpStatus = 401))
                } else {
                    ApiResult.Success(testUser)
                }
            },
        )
        val sessionManager = SessionManager(store, repository, this)

        sessionManager.bootstrap()
        advanceUntilIdle()

        assertEquals(SessionState.Authenticated(testUser), sessionManager.state.value)
        assertEquals(refreshed, store.tokens)
    }

    @Test
    fun `rejected refresh clears tokens and signs out`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeTokenStore(testTokens)
        val repository = FakeAuthRepository(
            refreshResult = {
                ApiResult.Failure(ApiError(ApiErrorKind.HTTP, code = "INVALID_TOKEN", httpStatus = 401))
            },
            currentUserResult = {
                ApiResult.Failure(ApiError(ApiErrorKind.HTTP, code = "UNAUTHORIZED", httpStatus = 401))
            },
        )
        val sessionManager = SessionManager(store, repository, this)

        sessionManager.bootstrap()
        advanceUntilIdle()

        assertEquals(SessionState.Unauthenticated, sessionManager.state.value)
        assertNull(store.tokens)
    }

    @Test
    fun `launch without a reachable server keeps tokens and offers retry`() =
        runTest(mainDispatcherRule.dispatcher) {
            val store = FakeTokenStore(testTokens)
            val repository = FakeAuthRepository(
                currentUserResult = { ApiResult.Failure(ApiError(ApiErrorKind.NETWORK)) },
            )
            val sessionManager = SessionManager(store, repository, this)

            sessionManager.bootstrap()
            advanceUntilIdle()

            assertEquals(SessionState.Unavailable, sessionManager.state.value)
            assertEquals(testTokens, store.tokens)
        }

    @Test
    fun `logout clears local state and revokes the refresh token`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeTokenStore(testTokens)
        val repository = FakeAuthRepository()
        val sessionManager = SessionManager(store, repository, this)
        sessionManager.onTokens(testTokens)

        sessionManager.logout()
        advanceUntilIdle()

        assertEquals(SessionState.Unauthenticated, sessionManager.state.value)
        assertNull(store.tokens)
        assertEquals(1, repository.logoutCalls)
        assertTrue(sessionManager.accessToken == null && sessionManager.refreshToken == null)
    }
}
