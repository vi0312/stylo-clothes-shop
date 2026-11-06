package com.stylo.shop.feature.profile

import app.cash.turbine.test
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.profile.model.AddressInput
import com.stylo.shop.feature.profile.ui.address.AddressListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddressListViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `load exposes addresses`() = runTest {
        val repository = FakeProfileRepository(
            listResult = ApiResult.Success(listOf(testDefaultAddress, testSecondAddress)),
        )
        val viewModel = AddressListViewModel(repository)

        viewModel.load()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val itemsState = viewModel.uiState.value.itemsState
        assertTrue(itemsState is UiState.Content)
        assertEquals(
            listOf(testDefaultAddress, testSecondAddress),
            (itemsState as UiState.Content).data,
        )
    }

    @Test
    fun `load failure exposes the error for retry`() = runTest {
        val repository = FakeProfileRepository(
            listResult = ApiResult.Failure(
                ApiError(ApiErrorKind.HTTP, code = "INTERNAL", httpStatus = 500),
            ),
        )
        val viewModel = AddressListViewModel(repository)

        viewModel.load()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val itemsState = viewModel.uiState.value.itemsState
        assertTrue(itemsState is UiState.Error)
        assertEquals("INTERNAL", (itemsState as UiState.Error).error.code)
    }

    @Test
    fun `setting a default sends the current fields and reloads the list`() = runTest {
        val repository = FakeProfileRepository(
            listResult = ApiResult.Success(listOf(testDefaultAddress, testSecondAddress)),
            updateResult = ApiResult.Success(testSecondAddress.copy(isDefault = true)),
        )
        val viewModel = AddressListViewModel(repository)
        viewModel.load()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.setDefault(testSecondAddress)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(
                testSecondAddress.id to AddressInput(
                    recipientName = testSecondAddress.recipientName,
                    phone = testSecondAddress.phone,
                    line = testSecondAddress.line,
                    isDefault = true,
                ),
            ),
            repository.updated,
        )
        assertEquals(2, repository.listCalls)
        assertNull(viewModel.uiState.value.defaultingId)
    }

    @Test
    fun `setting the already default address is a no-op`() = runTest {
        val repository = FakeProfileRepository(
            listResult = ApiResult.Success(listOf(testDefaultAddress)),
        )
        val viewModel = AddressListViewModel(repository)

        viewModel.setDefault(testDefaultAddress)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun `deleting the selected address sends the id and reloads`() = runTest {
        val repository = FakeProfileRepository(
            listResult = ApiResult.Success(listOf(testDefaultAddress, testSecondAddress)),
        )
        val viewModel = AddressListViewModel(repository)
        viewModel.load()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.requestDelete(testSecondAddress)
            viewModel.confirmDelete()
            dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(testSecondAddress.id), repository.deleted)
            assertEquals(UiEffect.ShowMessageRes(R.string.address_deleted), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, repository.listCalls)
        assertNull(viewModel.uiState.value.confirmDelete)
    }

    @Test
    fun `delete failure keeps the dialog closed and reports the error`() = runTest {
        val repository = FakeProfileRepository(
            deleteResult = ApiResult.Failure(
                ApiError(ApiErrorKind.HTTP, code = "INTERNAL", httpStatus = 500),
            ),
        )
        val viewModel = AddressListViewModel(repository)

        viewModel.effects.test {
            viewModel.requestDelete(testSecondAddress)
            viewModel.confirmDelete()
            dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                UiEffect.ShowError(ApiError(ApiErrorKind.HTTP, code = "INTERNAL", httpStatus = 500)),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, repository.listCalls)
        assertNull(viewModel.uiState.value.confirmDelete)
    }
}
