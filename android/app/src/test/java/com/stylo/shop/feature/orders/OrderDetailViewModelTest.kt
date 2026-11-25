package com.stylo.shop.feature.orders

import androidx.lifecycle.SavedStateHandle
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.orders.model.OrderStatus
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

    @Test
    fun `cancel is offered until the order is delivered, even after payment`() = runTest {
        for (status in listOf(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPING)) {
            val viewModel = viewModel(
                repository = FakeOrdersRepository(
                    result = ApiResult.Success(
                        testOrder(
                            status = status,
                            paidAt = "2026-11-04T04:00:00.000Z",
                        ),
                    ),
                ),
            )
            advance()
            assertTrue("$status should be cancellable", viewModel.uiState.value.canCancel)
        }

        for (status in listOf(OrderStatus.DELIVERED, OrderStatus.CANCELLED)) {
            val viewModel = viewModel(
                repository = FakeOrdersRepository(
                    result = ApiResult.Success(testOrder(status = status)),
                ),
            )
            advance()
            assertTrue("$status must not be cancellable", !viewModel.uiState.value.canCancel)
        }
    }

    @Test
    fun `cancelling replaces the order with the cancelled one`() = runTest {
        val repository = FakeOrdersRepository(
            cancelResult = ApiResult.Success(testOrder(status = OrderStatus.CANCELLED)),
        )
        val viewModel = viewModel(repository = repository)
        advance()

        viewModel.cancel("  Đặt nhầm size  ")
        advance()

        assertEquals(listOf(123L), repository.cancelledIds)
        assertEquals(listOf("Đặt nhầm size"), repository.cancelReasons)
        val state = viewModel.uiState.value
        assertEquals(OrderStatus.CANCELLED, (state.orderState as UiState.Content).data.status)
        assertTrue(!state.isCancelling)
        assertTrue(!state.canCancel)
    }

    @Test
    fun `a rejected cancel surfaces the error, refetches and clears on dismiss`() = runTest {
        val repository = FakeOrdersRepository(
            cancelResult = ApiResult.Failure(
                ApiError(kind = ApiErrorKind.HTTP, code = "INVALID_ORDER_STATE", httpStatus = 409),
            ),
        )
        val viewModel = viewModel(repository = repository)
        advance()

        viewModel.cancel("Đổi ý")
        advance()

        assertEquals("INVALID_ORDER_STATE", viewModel.uiState.value.cancelError?.code)
        // Initial load plus the refetch that shows what the server says now.
        assertEquals(2, repository.requestedIds.size)
        assertTrue(!viewModel.uiState.value.isCancelling)

        viewModel.dismissCancelError()
        assertEquals(null, viewModel.uiState.value.cancelError)
    }

    @Test
    fun `a blank reason never posts a cancel`() = runTest {
        val repository = FakeOrdersRepository()
        val viewModel = viewModel(repository = repository)
        advance()

        viewModel.cancel("   ")
        advance()

        assertTrue(repository.cancelledIds.isEmpty())
    }

    @Test
    fun `a second tap while cancelling does not post twice`() = runTest {
        val repository = FakeOrdersRepository()
        val viewModel = viewModel(repository = repository)
        advance()

        viewModel.cancel("Đổi ý")
        viewModel.cancel("Đổi ý")
        advance()

        assertEquals(1, repository.cancelledIds.size)
    }
}
