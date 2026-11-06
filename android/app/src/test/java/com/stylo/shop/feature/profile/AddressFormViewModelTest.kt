package com.stylo.shop.feature.profile

import androidx.lifecycle.SavedStateHandle
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.profile.model.AddressInput
import com.stylo.shop.feature.profile.model.ProfileFieldError
import com.stylo.shop.feature.profile.ui.address.AddressFormViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddressFormViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `create route starts empty without loading`() = runTest {
        val viewModel = AddressFormViewModel(FakeProfileRepository(), SavedStateHandle())

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertNull(state.addressId)
        assertEquals("", state.recipientName)
    }

    @Test
    fun `create save sends trimmed fields`() = runTest {
        val repository = FakeProfileRepository(
            createResult = ApiResult.Success(testSecondAddress),
        )
        val viewModel = AddressFormViewModel(repository, SavedStateHandle())

        viewModel.onRecipientNameChange("  Trần Thị Bình  ")
        viewModel.onPhoneChange(" 0912345678 ")
        viewModel.onLineChange(" 99 Lê Lợi, Q.1 ")
        viewModel.onDefaultChange(true)
        viewModel.save()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            AddressInput(
                recipientName = "Trần Thị Bình",
                phone = "0912345678",
                line = "99 Lê Lợi, Q.1",
                isDefault = true,
            ),
            repository.created.single(),
        )
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `missing required fields block the call`() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = AddressFormViewModel(repository, SavedStateHandle())

        viewModel.save()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProfileFieldError.REQUIRED, state.recipientNameError)
        assertEquals(ProfileFieldError.REQUIRED, state.phoneError)
        assertEquals(ProfileFieldError.REQUIRED, state.lineError)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `edit route loads and prefills the address`() = runTest {
        val repository = FakeProfileRepository(
            listResult = ApiResult.Success(listOf(testDefaultAddress, testSecondAddress)),
        )
        val viewModel = AddressFormViewModel(
            repository,
            SavedStateHandle(mapOf("addressId" to testSecondAddress.id)),
        )
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(testSecondAddress.id, state.addressId)
        assertEquals(testSecondAddress.recipientName, state.recipientName)
        assertEquals(testSecondAddress.phone, state.phone)
        assertEquals(testSecondAddress.line, state.line)
        assertFalse(state.isDefault)
        assertFalse(state.isDefaultLocked)
    }

    @Test
    fun `editing the current default locks the default toggle`() = runTest {
        val repository = FakeProfileRepository(
            listResult = ApiResult.Success(listOf(testDefaultAddress)),
        )
        val viewModel = AddressFormViewModel(
            repository,
            SavedStateHandle(mapOf("addressId" to testDefaultAddress.id)),
        )
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDefaultLocked)

        viewModel.onDefaultChange(false)

        assertTrue(viewModel.uiState.value.isDefault)
    }

    @Test
    fun `edit save targets the address id`() = runTest {
        val repository = FakeProfileRepository(
            listResult = ApiResult.Success(listOf(testDefaultAddress, testSecondAddress)),
            updateResult = ApiResult.Success(testSecondAddress),
        )
        val viewModel = AddressFormViewModel(
            repository,
            SavedStateHandle(mapOf("addressId" to testSecondAddress.id)),
        )
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onLineChange("Địa chỉ mới")
        viewModel.onDefaultChange(true)
        viewModel.save()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val (id, input) = repository.updated.single()
        assertEquals(testSecondAddress.id, id)
        assertEquals("Địa chỉ mới", input.line)
        assertTrue(input.isDefault)
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `missing address surfaces NOT_FOUND`() = runTest {
        val repository = FakeProfileRepository(listResult = ApiResult.Success(emptyList()))
        val viewModel = AddressFormViewModel(
            repository,
            SavedStateHandle(mapOf("addressId" to 99L)),
        )
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals("NOT_FOUND", state.loadError?.code)
    }

    @Test
    fun `server failure keeps the form open`() = runTest {
        val repository = FakeProfileRepository(
            createResult = ApiResult.Failure(
                ApiError(ApiErrorKind.HTTP, code = "INTERNAL", httpStatus = 500),
            ),
        )
        val viewModel = AddressFormViewModel(repository, SavedStateHandle())

        viewModel.onRecipientNameChange("Trần Thị Bình")
        viewModel.onPhoneChange("0912345678")
        viewModel.onLineChange("99 Lê Lợi, Q.1")
        viewModel.save()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("INTERNAL", viewModel.uiState.value.formError?.code)
        assertFalse(viewModel.uiState.value.saved)
    }
}
