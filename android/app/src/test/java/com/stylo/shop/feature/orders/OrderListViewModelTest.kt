package com.stylo.shop.feature.orders

import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.orders.model.OrderStatus
import com.stylo.shop.feature.orders.model.OrderSummary
import com.stylo.shop.feature.orders.ui.OrderListViewModel
import com.stylo.shop.feature.orders.ui.OrderStatusFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderListViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun advance() = dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    private fun content(viewModel: OrderListViewModel): List<OrderSummary> =
        (viewModel.uiState.value.ordersState as UiState.Content).data

    @Test
    fun `loads the first page without a status filter`() = runTest {
        val repository = FakeOrdersRepository()
        val viewModel = OrderListViewModel(repository)
        advance()

        assertEquals(1, repository.queries.size)
        assertNull(repository.queries.single().status)
        assertEquals(1, repository.queries.single().page)
        assertEquals(listOf(testOrderSummary()), content(viewModel))
    }

    @Test
    fun `choosing a status chip asks the server for that status and restarts at page one`() =
        runTest {
            val repository = FakeOrdersRepository()
            val viewModel = OrderListViewModel(repository)
            advance()

            viewModel.onFilterSelected(OrderStatusFilter.CANCELLED)
            advance()

            assertEquals(OrderStatus.CANCELLED, repository.queries.last().status)
            assertEquals(1, repository.queries.last().page)
            assertEquals(OrderStatusFilter.CANCELLED, viewModel.uiState.value.filter)
        }

    @Test
    fun `load more appends the next page`() = runTest {
        val first = (1L..20L).map { testOrderSummary(id = it, code = "STY-$it") }
        val second = (21L..25L).map { testOrderSummary(id = it, code = "STY-$it") }
        val repository = FakeOrdersRepository(
            pages = listOf(
                ApiResult.Success(testOrderPage(items = first, page = 1, total = 25)),
                ApiResult.Success(testOrderPage(items = second, page = 2, total = 25)),
            ),
        )
        val viewModel = OrderListViewModel(repository)
        advance()

        assertTrue(viewModel.uiState.value.hasMore)
        viewModel.onLoadMore()
        advance()

        assertEquals(2, repository.queries.last().page)
        assertEquals(25, content(viewModel).size)
        assertEquals(false, viewModel.uiState.value.hasMore)
    }

    @Test
    fun `a failed page keeps the loaded rows and offers a retry`() = runTest {
        val first = (1L..20L).map { testOrderSummary(id = it) }
        val repository = FakeOrdersRepository(
            pages = listOf(
                ApiResult.Success(testOrderPage(items = first, page = 1, total = 40)),
                ApiResult.Failure(ApiError(kind = ApiErrorKind.NETWORK)),
            ),
        )
        val viewModel = OrderListViewModel(repository)
        advance()

        viewModel.onLoadMore()
        advance()

        assertEquals(20, content(viewModel).size)
        assertTrue(viewModel.uiState.value.loadMoreError != null)
    }

    @Test
    fun `an empty page is content, not an error`() = runTest {
        val repository = FakeOrdersRepository(
            pages = listOf(ApiResult.Success(testOrderPage(items = emptyList(), total = 0))),
        )
        val viewModel = OrderListViewModel(repository)
        advance()

        assertEquals(emptyList<OrderSummary>(), content(viewModel))
    }

    @Test
    fun `a failed first load becomes an error state that retry clears`() = runTest {
        val repository = FakeOrdersRepository(
            pages = listOf(ApiResult.Failure(ApiError(kind = ApiErrorKind.TIMEOUT))),
        )
        val viewModel = OrderListViewModel(repository)
        advance()
        assertTrue(viewModel.uiState.value.ordersState is UiState.Error)

        repository.pages = listOf(ApiResult.Success(testOrderPage()))
        viewModel.onRetry()
        advance()

        assertTrue(viewModel.uiState.value.ordersState is UiState.Content)
    }

    @Test
    fun `refresh after a cancel keeps the current rows on screen`() = runTest {
        val repository = FakeOrdersRepository()
        val viewModel = OrderListViewModel(repository)
        advance()

        repository.pages = listOf(
            ApiResult.Success(
                testOrderPage(items = listOf(testOrderSummary(status = OrderStatus.CANCELLED))),
            ),
        )
        viewModel.refreshIfLoaded()
        advance()

        assertEquals(OrderStatus.CANCELLED, content(viewModel).single().status)
    }
}
