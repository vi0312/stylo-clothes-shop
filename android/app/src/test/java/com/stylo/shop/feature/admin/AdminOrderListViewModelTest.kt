package com.stylo.shop.feature.admin

import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.model.AdminOrderRange
import com.stylo.shop.feature.admin.model.AdminOrderSummary
import com.stylo.shop.feature.admin.model.Paginated
import com.stylo.shop.feature.admin.model.window
import com.stylo.shop.feature.admin.ui.orders.AdminOrderListViewModel
import com.stylo.shop.feature.admin.ui.orders.AdminOrderStatusFilter
import com.stylo.shop.feature.orders.model.OrderStatus
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminOrderListViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun advance() = dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    private fun content(viewModel: AdminOrderListViewModel): List<AdminOrderSummary> =
        (viewModel.uiState.value.ordersState as UiState.Content).data

    @Test
    fun `the default filter asks for the newest orders with no status or date window`() = runTest {
        val repository = FakeAdminOrdersRepository()
        val viewModel = AdminOrderListViewModel(repository)
        advance()

        val query = repository.queries.single()
        assertNull(query.status)
        assertNull(query.q)
        assertNull(query.from)
        assertNull(query.to)
        assertEquals(1, query.page)
        assertEquals(1, content(viewModel).size)
    }

    @Test
    fun `a status chip filters on the server and restarts paging`() = runTest {
        val repository = FakeAdminOrdersRepository()
        val viewModel = AdminOrderListViewModel(repository)
        advance()

        viewModel.onStatusFilterSelected(AdminOrderStatusFilter.SHIPPING)
        advance()

        assertEquals(OrderStatus.SHIPPING, repository.queries.last().status)
        assertEquals(1, repository.queries.last().page)
    }

    @Test
    fun `the date range presets become inclusive business days`() = runTest {
        val today = LocalDate.of(2026, 11, 7)

        assertEquals(null to null, AdminOrderRange.ALL.window(today))
        assertEquals("2026-11-07" to "2026-11-07", AdminOrderRange.TODAY.window(today))
        assertEquals("2026-11-01" to "2026-11-07", AdminOrderRange.LAST_7.window(today))
        assertEquals("2026-10-09" to "2026-11-07", AdminOrderRange.LAST_30.window(today))
    }

    @Test
    fun `choosing a range sends from and to dates`() = runTest {
        val repository = FakeAdminOrdersRepository()
        val viewModel = AdminOrderListViewModel(repository)
        advance()

        viewModel.onRangeSelected(AdminOrderRange.LAST_30)
        advance()

        val today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
        val query = repository.queries.last()
        assertEquals(today.minusDays(29).toString(), query.from)
        assertEquals(today.toString(), query.to)
    }

    @Test
    fun `search trims the term and only runs on submit`() = runTest {
        val repository = FakeAdminOrdersRepository()
        val viewModel = AdminOrderListViewModel(repository)
        advance()

        viewModel.onSearchQueryChange("  STY-261104  ")
        advance()
        assertEquals(1, repository.queries.size)

        viewModel.onSearch()
        advance()

        assertEquals("STY-261104", repository.queries.last().q)
    }

    @Test
    fun `load more appends the next page and stops at the total`() = runTest {
        val first = (1L..20L).map { testAdminOrderSummary(id = it) }
        val second = (21L..25L).map { testAdminOrderSummary(id = it) }
        val repository = FakeAdminOrdersRepository(
            pages = listOf(
                ApiResult.Success(Paginated(first, page = 1, limit = 20, total = 25)),
                ApiResult.Success(Paginated(second, page = 2, limit = 20, total = 25)),
            ),
        )
        val viewModel = AdminOrderListViewModel(repository)
        advance()

        viewModel.onLoadMore()
        advance()

        assertEquals(2, repository.queries.last().page)
        assertEquals(25, content(viewModel).size)
        assertTrue(!viewModel.uiState.value.hasMore)
    }

    @Test
    fun `a failed first page becomes an error state`() = runTest {
        val repository = FakeAdminOrdersRepository(
            pages = listOf(ApiResult.Failure(ApiError(kind = ApiErrorKind.NETWORK))),
        )
        val viewModel = AdminOrderListViewModel(repository)
        advance()

        assertTrue(viewModel.uiState.value.ordersState is UiState.Error)
    }
}
