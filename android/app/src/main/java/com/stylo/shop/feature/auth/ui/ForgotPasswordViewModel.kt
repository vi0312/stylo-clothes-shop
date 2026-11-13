package com.stylo.shop.feature.auth.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class ForgotPasswordStep { EMAIL, CODE, DONE }

data class ForgotPasswordErrors(
    val email: AuthFieldError? = null,
    val code: AuthFieldError? = null,
    val password: AuthFieldError? = null,
) {
    val hasAny: Boolean
        get() = email != null || code != null || password != null
}

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val errors: ForgotPasswordErrors = ForgotPasswordErrors(),
    @StringRes val formError: Int? = null,
    val submitting: Boolean = false,
    /** Seconds left before "Gửi lại mã" is allowed; mirrors PASSWORD_RESET_RESEND_SECONDS. */
    val resendSeconds: Int = 0,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    private var resendJob: Job? = null

    fun onEmailChange(value: String) = _state.update {
        it.copy(email = value, errors = it.errors.copy(email = null), formError = null)
    }

    fun onCodeChange(value: String) = _state.update {
        it.copy(
            code = value.filter { char -> char.isDigit() }.take(CODE_LENGTH),
            errors = it.errors.copy(code = null),
            formError = null,
        )
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, errors = it.errors.copy(password = null), formError = null)
    }

    /** Back to step one without dropping what the user typed. */
    fun onChangeEmail() {
        resendJob?.cancel()
        _state.update {
            it.copy(step = ForgotPasswordStep.EMAIL, code = "", password = "", resendSeconds = 0)
        }
    }

    fun requestCode() {
        val snapshot = _state.value
        if (snapshot.submitting) return

        val email = snapshot.email.trim()
        val emailError = when {
            email.isEmpty() -> AuthFieldError.Required
            email.length > EMAIL_MAX -> AuthFieldError.TooLong(EMAIL_MAX)
            !EMAIL_PATTERN.matches(email) -> AuthFieldError.InvalidEmail
            else -> null
        }
        if (emailError != null) {
            _state.update { it.copy(errors = it.errors.copy(email = emailError), formError = null) }
            return
        }

        _state.update { it.copy(submitting = true, errors = ForgotPasswordErrors(), formError = null) }
        viewModelScope.launch {
            when (val result = authRepository.requestPasswordReset(email)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            submitting = false,
                            step = ForgotPasswordStep.CODE,
                            email = email,
                            code = "",
                            password = "",
                        )
                    }
                    startResendCountdown()
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(submitting = false, formError = ErrorMessages.resourceId(result.error))
                }
            }
        }
    }

    fun submit() {
        val snapshot = _state.value
        if (snapshot.submitting) return

        val password = snapshot.password
        val errors = ForgotPasswordErrors(
            code = if (snapshot.code.length == CODE_LENGTH) null else AuthFieldError.InvalidCode,
            password = when {
                password.isEmpty() -> AuthFieldError.Required
                password.length < PASSWORD_MIN -> AuthFieldError.PasswordTooShort
                password.length > PASSWORD_MAX -> AuthFieldError.TooLong(PASSWORD_MAX)
                else -> null
            },
        )
        if (errors.hasAny) {
            _state.update { it.copy(errors = errors, formError = null) }
            return
        }

        _state.update { it.copy(submitting = true, errors = ForgotPasswordErrors(), formError = null) }
        viewModelScope.launch {
            val result = authRepository.resetPassword(snapshot.email, snapshot.code, password)
            when (result) {
                is ApiResult.Success -> {
                    resendJob?.cancel()
                    _state.update { it.copy(submitting = false, step = ForgotPasswordStep.DONE) }
                }
                is ApiResult.Failure -> _state.update { current ->
                    val inline = inlineErrors(result.error)
                    current.copy(
                        submitting = false,
                        errors = inline ?: current.errors,
                        formError = if (inline != null) null else ErrorMessages.resourceId(result.error),
                    )
                }
            }
        }
    }

    private fun startResendCountdown() {
        resendJob?.cancel()
        _state.update { it.copy(resendSeconds = RESEND_SECONDS) }
        resendJob = viewModelScope.launch {
            while (_state.value.resendSeconds > 0) {
                delay(1_000)
                _state.update { it.copy(resendSeconds = (it.resendSeconds - 1).coerceAtLeast(0)) }
            }
        }
    }

    private fun inlineErrors(error: ApiError): ForgotPasswordErrors? {
        val items = error.details as? JsonArray ?: return null
        var errors = ForgotPasswordErrors()
        items.forEach { item ->
            val field = ((item as? JsonObject)?.get("field") as? JsonPrimitive)?.contentOrNull
            errors = when (field) {
                "email" -> errors.copy(email = AuthFieldError.Invalid)
                "code" -> errors.copy(code = AuthFieldError.InvalidCode)
                "password" -> errors.copy(password = AuthFieldError.Invalid)
                else -> errors
            }
        }
        return errors.takeIf { it.hasAny }
    }

    private companion object {
        const val EMAIL_MAX = 255
        const val PASSWORD_MIN = 8
        const val PASSWORD_MAX = 100
        const val CODE_LENGTH = 6
        const val RESEND_SECONDS = 60
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
