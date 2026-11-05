package com.stylo.shop.core.storage

import com.stylo.shop.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class SessionState { LOADING, UNAUTHENTICATED, AUTHENTICATED }

@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    @ApplicationScope private val scope: CoroutineScope,
) {

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    private val _state = MutableStateFlow(SessionState.LOADING)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun bootstrap() {
        scope.launch {
            val tokens = tokenStore.read()
            if (tokens == null) {
                _state.value = SessionState.UNAUTHENTICATED
            } else {
                accessToken = tokens.accessToken
                refreshToken = tokens.refreshToken
                _state.value = SessionState.AUTHENTICATED
            }
        }
    }

    suspend fun onTokens(tokens: TokenPair) {
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
        _state.value = SessionState.AUTHENTICATED
        tokenStore.save(tokens)
    }

    /** Synchronous: the OkHttp authenticator ends the session from its own thread. */
    fun onRefreshFailed() {
        accessToken = null
        refreshToken = null
        _state.value = SessionState.UNAUTHENTICATED
        scope.launch { tokenStore.clear() }
    }
}
