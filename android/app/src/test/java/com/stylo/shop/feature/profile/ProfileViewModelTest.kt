package com.stylo.shop.feature.profile

import com.stylo.shop.FakeAuthRepository
import com.stylo.shop.FakeTokenStore
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.storage.SessionManager
import com.stylo.shop.core.storage.SessionState
import com.stylo.shop.feature.profile.model.ProfileFieldError
import com.stylo.shop.feature.profile.ui.ProfileViewModel
import com.stylo.shop.testSession
import com.stylo.shop.testTokens
import com.stylo.shop.testUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private suspend fun signedInSession(scope: kotlinx.coroutines.CoroutineScope): SessionManager {
        val sessionManager = SessionManager(FakeTokenStore(testTokens), FakeAuthRepository(), scope)
        sessionManager.onAuthenticated(testSession)
        return sessionManager
    }

    @Test
    fun `state starts from the signed-in user`() = runTest {
        val viewModel = ProfileViewModel(FakeProfileRepository(), signedInSession(backgroundScope))

        assertEquals(testUser.email, viewModel.uiState.value.email)
        assertEquals(testUser.fullName, viewModel.uiState.value.fullName)
        assertEquals(testUser.phone, viewModel.uiState.value.phone)
    }

    @Test
    fun `save trims fields and updates the session user`() = runTest {
        val sessionManager = signedInSession(backgroundScope)
        val repository = FakeProfileRepository(
            updateProfileResult = ApiResult.Success(
                testUser.copy(fullName = "Trần Bình", phone = "0912345678"),
            ),
        )
        val viewModel = ProfileViewModel(repository, sessionManager)

        viewModel.onFullNameChange("  Trần Bình  ")
        viewModel.onPhoneChange(" 0912345678 ")
        viewModel.save()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Trần Bình" to "0912345678"), repository.updateProfileCalls)
        assertEquals("Trần Bình", viewModel.uiState.value.fullName)
        assertFalse(viewModel.uiState.value.saving)

        val sessionUser = (sessionManager.state.value as SessionState.Authenticated).user
        assertEquals("Trần Bình", sessionUser.fullName)
        assertEquals("0912345678", sessionUser.phone)
    }

    @Test
    fun `blank name is rejected before calling the server`() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = ProfileViewModel(repository, signedInSession(backgroundScope))

        viewModel.onFullNameChange("   ")
        viewModel.save()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileFieldError.REQUIRED, viewModel.uiState.value.fullNameError)
        assertTrue(repository.updateProfileCalls.isEmpty())
    }

    @Test
    fun `too-long phone is rejected before calling the server`() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = ProfileViewModel(repository, signedInSession(backgroundScope))

        viewModel.onPhoneChange("0".repeat(21))
        viewModel.save()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileFieldError.TOO_LONG, viewModel.uiState.value.phoneError)
        assertTrue(repository.updateProfileCalls.isEmpty())
    }

    @Test
    fun `server failure keeps the form editable and exposes the error`() = runTest {
        val repository = FakeProfileRepository(
            updateProfileResult = ApiResult.Failure(
                ApiError(ApiErrorKind.HTTP, code = "INTERNAL", httpStatus = 500),
            ),
        )
        val viewModel = ProfileViewModel(repository, signedInSession(backgroundScope))

        viewModel.save()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("INTERNAL", state.formError?.code)
        assertFalse(state.saving)
        assertEquals(testUser.fullName, state.fullName)
    }
}
