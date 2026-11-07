package com.stylo.shop.feature.admin

import androidx.lifecycle.SavedStateHandle
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.model.AdminReceiptDetail
import com.stylo.shop.feature.admin.ui.inventory.AdminReceiptDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminReceiptDetailViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun advance() = dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    private fun viewModel(
        repository: FakeAdminInventoryRepository,
        receiptId: Long = 12,
    ): AdminReceiptDetailViewModel = AdminReceiptDetailViewModel(
        repository = repository,
        savedStateHandle = SavedStateHandle(mapOf("receiptId" to receiptId)),
    )

    @Test
    fun `loads the receipt from the route id`() = runTest {
        val repository = FakeAdminInventoryRepository(
            detail = ApiResult.Success(testReceiptDetail(id = 12, items = listOf(testReceiptItem()))),
        )
        val model = viewModel(repository)
        advance()

        val state = model.uiState.value.receiptState
        assertTrue(state is UiState.Content)
        assertEquals(12L, (state as UiState.Content).data.id)
        assertEquals(1, repository.detailRequests)
    }

    @Test
    fun `a failed load becomes an error state and retry reloads`() = runTest {
        val repository = FakeAdminInventoryRepository(
            detail = ApiResult.Failure(
                ApiError(kind = ApiErrorKind.HTTP, httpStatus = 404, code = "NOT_FOUND"),
            ),
        )
        val model = viewModel(repository)
        advance()

        assertTrue(model.uiState.value.receiptState is UiState.Error)

        repository.detail = ApiResult.Success(testReceiptDetail(id = 12))
        model.load()
        advance()

        assertTrue(model.uiState.value.receiptState is UiState.Content)
        assertEquals(2, repository.detailRequests)
    }
}
