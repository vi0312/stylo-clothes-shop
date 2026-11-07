package com.stylo.shop.feature.orders

import androidx.lifecycle.SavedStateHandle
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.orders.ui.OrderDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun viewModel(
        repository: FakeOrdersRepository = FakeOrdersRepository(),
        orderId: Long = 123,
    ): OrderDetailViewModel = OrderDetailViewModel(
        repository = repository,
        savedStateHandle = SavedStateHandle(mapOf("orderId" to orderId)),
    )

    private fun advance() = dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `loads the order named by the route`() = runTest {
        val repository = FakeOrdersRepository(result = ApiResult.Success(testOrder(id = 5)))
        val viewModel = viewModel(repository = repository, orderId = 5)
        advance()

        assertEquals(listOf(5L), repository.requestedIds)
        val state = viewModel.uiState.value.orderState
        assertTrue(state is UiState.Content)
        assertEquals(testOrder(id = 5), (state as UiState.Content).data)
    }

    @Test
    fun `refresh re-reads the order without blanking the screen`() = runTest {
        val repository = FakeOrdersRepository()
        val viewModel = viewModel(repository = repository)
        advance()

        viewModel.refresh()
        assertTrue(viewModel.uiState.value.orderState is UiState.Content)

        advance()
        assertEquals(2, repository.requestedIds.size)
    }

    @Test
    fun `a failed load becomes an error state that retry can clear`() = runTest {
        val repository = FakeOrdersRepository(
            result = ApiResult.Failure(ApiError(kind = ApiErrorKind.NETWORK)),
        )
        val viewModel = viewModel(repository = repository)
        advance()
        assertTrue(viewModel.uiState.value.orderState is UiState.Error)

        repository.result = ApiResult.Success(testOrder())
        viewModel.retry()
        advance()

        assertTrue(viewModel.uiState.value.orderState is UiState.Content)
    }
}
