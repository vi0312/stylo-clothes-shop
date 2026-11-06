package com.stylo.shop.feature.auth

import com.stylo.shop.FakeAuthRepository
import com.stylo.shop.FakeTokenStore
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.storage.SessionManager
import com.stylo.shop.core.storage.SessionState
import com.stylo.shop.feature.auth.ui.AuthFieldError
import com.stylo.shop.feature.auth.ui.AuthMode
import com.stylo.shop.feature.auth.ui.AuthViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `successful login stores the session and authenticates`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAuthRepository()
        val tokenStore = FakeTokenStore()
        val sessionManager = SessionManager(tokenStore, repository, backgroundScope)
        val viewModel = AuthViewModel(repository, sessionManager)

        viewModel.onEmailChange("an@example.com")
        viewModel.onPasswordChange("matkhau123")
        viewModel.submit(AuthMode.LOGIN)
        advanceUntilIdle()

        assertTrue(sessionManager.state.value is SessionState.Authenticated)
        assertEquals("access-token", tokenStore.tokens?.accessToken)
        assertNull(viewModel.state.value.formError)
    }

    @Test
    fun `rejected login shows the shared credential message and stays signed out`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAuthRepository(
                loginResult = ApiResult.Failure(
                    ApiError(ApiErrorKind.HTTP, code = "INVALID_CREDENTIALS", httpStatus = 401),
                ),
            )
            val sessionManager = SessionManager(FakeTokenStore(), repository, backgroundScope)
            val viewModel = AuthViewModel(repository, sessionManager)

            viewModel.onEmailChange("an@example.com")
            viewModel.onPasswordChange("wrong-password")
            viewModel.submit(AuthMode.LOGIN)
            advanceUntilIdle()

            assertEquals(R.string.error_invalid_credentials, viewModel.state.value.formError)
            assertEquals(false, viewModel.state.value.submitting)
            assertTrue(sessionManager.state.value !is SessionState.Authenticated)
        }

    @Test
    fun `register validates fields before calling the server`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository, SessionManager(FakeTokenStore(), repository, backgroundScope))

        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("short")
        viewModel.submit(AuthMode.REGISTER)
        advanceUntilIdle()

        assertEquals(0, repository.registerCalls)
        assertEquals(AuthFieldError.InvalidEmail, viewModel.state.value.errors.email)
        assertEquals(AuthFieldError.PasswordTooShort, viewModel.state.value.errors.password)
        assertEquals(AuthFieldError.Required, viewModel.state.value.errors.fullName)
    }

    @Test
    fun `email taken from the server becomes an inline error`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAuthRepository(
            registerResult = ApiResult.Failure(
                ApiError(ApiErrorKind.HTTP, code = "EMAIL_TAKEN", httpStatus = 409),
            ),
        )
        val viewModel = AuthViewModel(repository, SessionManager(FakeTokenStore(), repository, backgroundScope))

        viewModel.onEmailChange("an@example.com")
        viewModel.onPasswordChange("matkhau123")
        viewModel.onFullNameChange("Nguyễn Văn An")
        viewModel.submit(AuthMode.REGISTER)
        advanceUntilIdle()

        assertEquals(AuthFieldError.EmailTaken, viewModel.state.value.errors.email)
        assertEquals(false, viewModel.state.value.submitting)
    }

    @Test
    fun `validation details mark the matching fields`() = runTest(mainDispatcherRule.dispatcher) {
        val details = buildJsonArray {
            addJsonObject {
                put("field", "email")
                put("message", "Invalid email")
            }
            addJsonObject {
                put("field", "phone")
                put("message", "Too long")
            }
        }
        val repository = FakeAuthRepository(
            registerResult = ApiResult.Failure(
                ApiError(
                    ApiErrorKind.HTTP,
                    code = "VALIDATION_FAILED",
                    httpStatus = 400,
                    details = details,
                ),
            ),
        )
        val viewModel = AuthViewModel(repository, SessionManager(FakeTokenStore(), repository, backgroundScope))

        viewModel.onEmailChange("an@example.com")
        viewModel.onPasswordChange("matkhau123")
        viewModel.onFullNameChange("Nguyễn Văn An")
        viewModel.onPhoneChange("0901234567")
        viewModel.submit(AuthMode.REGISTER)
        advanceUntilIdle()

        assertEquals(AuthFieldError.Invalid, viewModel.state.value.errors.email)
        assertEquals(AuthFieldError.Invalid, viewModel.state.value.errors.phone)
        assertNull(viewModel.state.value.formError)
    }

    @Test
    fun `network failure on login keeps the transport message`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAuthRepository(
            loginResult = ApiResult.Failure(ApiError(ApiErrorKind.NETWORK)),
        )
        val viewModel = AuthViewModel(repository, SessionManager(FakeTokenStore(), repository, backgroundScope))

        viewModel.onEmailChange("an@example.com")
        viewModel.onPasswordChange("matkhau123")
        viewModel.submit(AuthMode.LOGIN)
        advanceUntilIdle()

        assertEquals(R.string.error_network, viewModel.state.value.formError)
    }
}
