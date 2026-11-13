package com.stylo.shop.feature.auth.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.storage.SessionManager
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class AuthMode { LOGIN, REGISTER }

sealed interface AuthFieldError {
    data object Required : AuthFieldError
    data object InvalidEmail : AuthFieldError
    data object PasswordTooShort : AuthFieldError
    data object EmailTaken : AuthFieldError
    data object Invalid : AuthFieldError
    data object InvalidCode : AuthFieldError
    data class TooLong(val max: Int) : AuthFieldError
}

data class AuthFieldErrors(
    val email: AuthFieldError? = null,
    val password: AuthFieldError? = null,
    val fullName: AuthFieldError? = null,
    val phone: AuthFieldError? = null,
) {
    val hasAny: Boolean
        get() = email != null || password != null || fullName != null || phone != null
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    val phone: String = "",
    val errors: AuthFieldErrors = AuthFieldErrors(),
    @StringRes val formError: Int? = null,
    val submitting: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update {
        it.copy(email = value, errors = it.errors.copy(email = null), formError = null)
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, errors = it.errors.copy(password = null), formError = null)
    }

    fun onFullNameChange(value: String) = _state.update {
        it.copy(fullName = value, errors = it.errors.copy(fullName = null), formError = null)
    }

    fun onPhoneChange(value: String) = _state.update {
        it.copy(phone = value, errors = it.errors.copy(phone = null), formError = null)
    }

    fun submit(mode: AuthMode) {
        val snapshot = _state.value
        if (snapshot.submitting) return

        val errors = validate(mode, snapshot)
        if (errors.hasAny) {
            _state.update { it.copy(errors = errors, formError = null) }
            return
        }

        _state.update { it.copy(submitting = true, errors = AuthFieldErrors(), formError = null) }
        viewModelScope.launch {
            val result = when (mode) {
                AuthMode.LOGIN -> authRepository.login(snapshot.email.trim(), snapshot.password)
                AuthMode.REGISTER -> authRepository.register(
                    email = snapshot.email.trim(),
                    password = snapshot.password,
                    fullName = snapshot.fullName.trim(),
                    phone = snapshot.phone.trim().ifEmpty { null },
                )
            }
            when (result) {
                is ApiResult.Success -> sessionManager.onAuthenticated(result.data)
                is ApiResult.Failure -> _state.update { current ->
                    failureState(mode, current, result.error)
                }
            }
        }
    }

    private fun validate(mode: AuthMode, state: AuthUiState): AuthFieldErrors {
        val email = state.email.trim()
        val password = state.password
        val fullName = state.fullName.trim()
        val phone = state.phone.trim()

        val emailError = when {
            email.isEmpty() -> AuthFieldError.Required
            mode == AuthMode.REGISTER && email.length > EMAIL_MAX -> AuthFieldError.TooLong(EMAIL_MAX)
            mode == AuthMode.REGISTER && !EMAIL_PATTERN.matches(email) -> AuthFieldError.InvalidEmail
            else -> null
        }
        val passwordError = when {
            password.isEmpty() -> AuthFieldError.Required
            mode == AuthMode.REGISTER && password.length < PASSWORD_MIN ->
                AuthFieldError.PasswordTooShort
            mode == AuthMode.REGISTER && password.length > PASSWORD_MAX ->
                AuthFieldError.TooLong(PASSWORD_MAX)
            else -> null
        }
        if (mode == AuthMode.LOGIN) {
            return AuthFieldErrors(email = emailError, password = passwordError)
        }
        return AuthFieldErrors(
            email = emailError,
            password = passwordError,
            fullName = when {
                fullName.isEmpty() -> AuthFieldError.Required
                fullName.length > NAME_MAX -> AuthFieldError.TooLong(NAME_MAX)
                else -> null
            },
            phone = if (phone.length > PHONE_MAX) AuthFieldError.TooLong(PHONE_MAX) else null,
        )
    }

    private fun failureState(mode: AuthMode, current: AuthUiState, error: ApiError): AuthUiState {
        val next = current.copy(submitting = false)
        if (mode == AuthMode.LOGIN) {
            // Any rejected sign-in is the same answer to the user; only transport failures differ.
            return next.copy(
                formError = if (error.kind == ApiErrorKind.HTTP) {
                    R.string.error_invalid_credentials
                } else {
                    ErrorMessages.resourceId(error)
                },
            )
        }
        if (error.code == CODE_EMAIL_TAKEN) {
            return next.copy(errors = next.errors.copy(email = AuthFieldError.EmailTaken))
        }
        if (error.code == CODE_VALIDATION_FAILED) {
            val inline = parseFieldErrors(error.details)
            if (inline.hasAny) return next.copy(errors = inline)
        }
        return next.copy(formError = ErrorMessages.resourceId(error))
    }

    private fun parseFieldErrors(details: JsonElement?): AuthFieldErrors {
        val items = details as? JsonArray ?: return AuthFieldErrors()
        var errors = AuthFieldErrors()
        items.forEach { item ->
            val field = ((item as? JsonObject)?.get("field") as? JsonPrimitive)?.contentOrNull
            errors = when (field) {
                "email" -> errors.copy(email = AuthFieldError.Invalid)
                "password" -> errors.copy(password = AuthFieldError.Invalid)
                "fullName" -> errors.copy(fullName = AuthFieldError.Invalid)
                "phone" -> errors.copy(phone = AuthFieldError.Invalid)
                else -> errors
            }
        }
        return errors
    }

    private companion object {
        const val EMAIL_MAX = 255
        const val PASSWORD_MIN = 8
        const val PASSWORD_MAX = 100
        const val NAME_MAX = 100
        const val PHONE_MAX = 20
        const val CODE_EMAIL_TAKEN = "EMAIL_TAKEN"
        const val CODE_VALIDATION_FAILED = "VALIDATION_FAILED"
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
