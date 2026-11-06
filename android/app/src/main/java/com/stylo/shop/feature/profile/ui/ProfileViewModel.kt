package com.stylo.shop.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.storage.SessionManager
import com.stylo.shop.core.storage.SessionState
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.feature.profile.data.ProfileRepository
import com.stylo.shop.feature.profile.model.PROFILE_NAME_MAX
import com.stylo.shop.feature.profile.model.PROFILE_PHONE_MAX
import com.stylo.shop.feature.profile.model.ProfileFieldError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val email: String = "",
    val fullName: String = "",
    val phone: String = "",
    val fullNameError: ProfileFieldError? = null,
    val phoneError: ProfileFieldError? = null,
    val saving: Boolean = false,
    val formError: ApiError? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        (sessionManager.state.value as? SessionState.Authenticated)?.user?.let { user ->
            ProfileUiState(
                email = user.email,
                fullName = user.fullName,
                phone = user.phone.orEmpty(),
            )
        } ?: ProfileUiState(),
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    fun onFullNameChange(value: String) {
        _uiState.update {
            it.copy(fullName = value, fullNameError = null, formError = null)
        }
    }

    fun onPhoneChange(value: String) {
        _uiState.update {
            it.copy(phone = value, phoneError = null, formError = null)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.saving) return

        val fullName = state.fullName.trim()
        val phone = state.phone.trim()
        val fullNameError = when {
            fullName.isEmpty() -> ProfileFieldError.REQUIRED
            fullName.length > PROFILE_NAME_MAX -> ProfileFieldError.TOO_LONG
            else -> null
        }
        val phoneError = if (phone.length > PROFILE_PHONE_MAX) ProfileFieldError.TOO_LONG else null
        if (fullNameError != null || phoneError != null) {
            _uiState.update {
                it.copy(fullNameError = fullNameError, phoneError = phoneError)
            }
            return
        }

        _uiState.update {
            it.copy(saving = true, fullNameError = null, phoneError = null, formError = null)
        }
        viewModelScope.launch {
            when (val result = repository.updateProfile(fullName = fullName, phone = phone)) {
                is ApiResult.Success -> {
                    sessionManager.updateUser(result.data)
                    _uiState.update {
                        it.copy(
                            saving = false,
                            email = result.data.email,
                            fullName = result.data.fullName,
                            phone = result.data.phone.orEmpty(),
                        )
                    }
                    _effects.send(UiEffect.ShowMessageRes(R.string.profile_saved))
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(saving = false, formError = result.error) }
                }
            }
        }
    }
}
