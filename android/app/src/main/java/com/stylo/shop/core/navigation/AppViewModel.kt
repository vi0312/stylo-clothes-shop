package com.stylo.shop.core.navigation

import androidx.lifecycle.ViewModel
import com.stylo.shop.core.storage.SessionEvent
import com.stylo.shop.core.storage.SessionManager
import com.stylo.shop.core.storage.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionManager.state
    val events: Flow<SessionEvent> = sessionManager.events

    fun retrySession() = sessionManager.retry()

    fun logout() = sessionManager.logout()
}
