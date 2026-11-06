package com.stylo.shop.core.storage

import com.stylo.shop.core.di.ApplicationScope
import com.stylo.shop.core.model.User
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.auth.data.AuthRepository
import com.stylo.shop.feature.auth.data.AuthSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Loading : SessionState
    data object Unauthenticated : SessionState
    data class Authenticated(val user: User) : SessionState

    /** Stored tokens exist, but the server could not confirm them; retry, don't sign out. */
    data object Unavailable : SessionState
}

sealed interface SessionEvent {
    data object Expired : SessionEvent
}

@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val authRepository: AuthRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _events = Channel<SessionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun bootstrap() {
        scope.launch { restore() }
    }

    fun retry() = bootstrap()

    /** Login/register success: persist the pair and let the root switch to the role graph. */
    suspend fun onAuthenticated(session: AuthSession) {
        onTokens(session.tokens)
        _state.value = SessionState.Authenticated(session.user)
    }

    suspend fun onTokens(tokens: TokenPair) {
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
        tokenStore.save(tokens)
    }

    /** The profile screen edits the signed-in user in place so every header reflects the save. */
    fun updateUser(user: User) {
        val current = _state.value
        if (current is SessionState.Authenticated) {
            _state.value = SessionState.Authenticated(user)
        }
    }

    /** Synchronous: OkHttp's authenticator ends the session from its own thread. */
    fun onRefreshFailed() {
        clearMemory()
        _state.value = SessionState.Unauthenticated
        _events.trySend(SessionEvent.Expired)
        scope.launch { tokenStore.clear() }
    }

    /** Local state drops at once; the server revoke is best effort and never blocks the UI. */
    fun logout() {
        val token = refreshToken
        clearMemory()
        _state.value = SessionState.Unauthenticated
        scope.launch {
            tokenStore.clear()
            if (token != null) authRepository.logout(token)
        }
    }

    private suspend fun restore() {
        _state.value = SessionState.Loading
        val stored = tokenStore.read()
        if (stored == null) {
            clearMemory()
            _state.value = SessionState.Unauthenticated
            return
        }
        accessToken = stored.accessToken
        refreshToken = stored.refreshToken

        when (val current = authRepository.currentUser(stored.accessToken)) {
            is ApiResult.Success -> _state.value = SessionState.Authenticated(current.data)
            is ApiResult.Failure -> {
                if (current.error.httpStatus == HTTP_UNAUTHORIZED) {
                    refreshAfterRejectedAccessToken(stored.refreshToken)
                } else {
                    settleLaunchFailure(current.error)
                }
            }
        }
    }

    private suspend fun refreshAfterRejectedAccessToken(storedRefreshToken: String) {
        when (val refreshed = authRepository.refresh(storedRefreshToken)) {
            is ApiResult.Success -> {
                onTokens(refreshed.data)
                when (val current = authRepository.currentUser(refreshed.data.accessToken)) {
                    is ApiResult.Success -> _state.value = SessionState.Authenticated(current.data)
                    is ApiResult.Failure -> settleLaunchFailure(current.error)
                }
            }
            is ApiResult.Failure -> settleLaunchFailure(refreshed.error)
        }
    }

    private suspend fun settleLaunchFailure(error: ApiError) {
        if (error.httpStatus == HTTP_UNAUTHORIZED || error.httpStatus == HTTP_FORBIDDEN) {
            clearMemory()
            tokenStore.clear()
            _state.value = SessionState.Unauthenticated
        } else {
            _state.value = SessionState.Unavailable
        }
    }

    private fun clearMemory() {
        accessToken = null
        refreshToken = null
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
