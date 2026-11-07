package com.stylo.shop.feature.admin

import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.admin.model.AdminDashboardUiState
import com.stylo.shop.feature.admin.model.AdminOrderQuery
import com.stylo.shop.feature.admin.model.DashboardBlock
import com.stylo.shop.feature.admin.model.LowStockRow
import com.stylo.shop.feature.admin.model.REPORT_ZONE
import com.stylo.shop.feature.admin.model.ReportGroupBy
import com.stylo.shop.feature.admin.model.RevenuePoint
import com.stylo.shop.feature.admin.model.RevenueReport
import com.stylo.shop.feature.admin.model.TopProductRow
import com.stylo.shop.feature.admin.model.dashboardChartSeries
import com.stylo.shop.feature.admin.ui.dashboard.AdminDashboardViewModel
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminDashboardViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), REPORT_ZONE)

    private fun advance() = dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    private fun viewModel(
        reports: FakeAdminReportsRepository = FakeAdminReportsRepository(),
        orders: FakeAdminOrdersRepository = FakeAdminOrdersRepository(),
    ) = AdminDashboardViewModel(reports, orders, clock)

    private fun failure(code: String = "BOOM") =
        ApiResult.Failure(ApiError(ApiErrorKind.HTTP, code = code))

    @Test
    fun `loads all four blocks with the default 30 day range`() = runTest {
        val reports = FakeAdminReportsRepository()
        reports.revenueResult = ApiResult.Success(
            RevenueReport(
                totalRevenue = 1_000_000,
                totalOrders = 3,
                series = listOf(RevenuePoint("2026-11-07", 1_000_000, 3)),
            ),
        )
        reports.topProductsResult = ApiResult.Success(
            listOf(TopProductRow(productId = 1, name = "Áo thun", quantitySold = 6, revenue = 600_000)),
        )
        reports.lowStockResult = ApiResult.Success(
            listOf(
                LowStockRow(
                    variantId = 1,
                    sku = "STY-AO-001-M",
                    productName = "Áo thun",
                    size = "M",
                    color = "Đen",
                    stock = 2,
                ),
            ),
        )
        val orders = FakeAdminOrdersRepository()
        val viewModel = viewModel(reports, orders)
        advance()

        // The current window plus the immediately preceding window of the same length, which
        // feeds the KPI deltas.
        assertEquals(
            listOf(
                RevenueCall(from = "2026-10-09", to = "2026-11-07", groupBy = ReportGroupBy.DAY),
                RevenueCall(from = "2026-09-09", to = "2026-10-08", groupBy = ReportGroupBy.DAY),
            ),
            reports.revenueCalls,
        )
        assertEquals("2026-10-09" to "2026-11-07", reports.topProductsCalls.single())
        assertEquals(3, reports.topProductsLimits.single())
        assertEquals(listOf(5), reports.lowStockCalls)
        assertEquals(
            AdminOrderQuery(
                from = "2026-10-09",
                to = "2026-11-07",
                limit = 3,
            ),
            orders.queries.single(),
        )
        val state = viewModel.uiState.value
        assertTrue(state.revenue is DashboardBlock.Ready)
        assertTrue(state.topProducts is DashboardBlock.Ready)
        assertTrue(state.lowStock is DashboardBlock.Ready)
        assertTrue(state.recentOrders is DashboardBlock.Ready)
    }

    @Test
    fun `computes the kpis from revenue and low stock`() = runTest {
        val reports = FakeAdminReportsRepository()
        reports.revenueResult = ApiResult.Success(
            RevenueReport(
                totalRevenue = 1_000_000,
                totalOrders = 3,
                series = emptyList(),
            ),
        )
        reports.lowStockResult = ApiResult.Success(
            listOf(
                LowStockRow(1, "A", "Áo", "M", "Đen", 1),
                LowStockRow(2, "B", "Quần", "L", "Xanh", 0),
            ),
        )
        val viewModel = viewModel(reports)
        advance()

        val kpis = viewModel.uiState.value.kpis
        assertEquals(1_000_000L, kpis.revenue)
        assertEquals(3, kpis.orders)
        assertEquals(333_333L, kpis.averageOrderValue)
        assertEquals(2, kpis.lowStockVariants)
    }

    @Test
    fun `average order value is zero without paid orders`() = runTest {
        val viewModel = viewModel()
        advance()

        assertEquals(0L, viewModel.uiState.value.kpis.averageOrderValue)
    }

    @Test
    fun `one failing block leaves the other blocks visible`() = runTest {
        val reports = FakeAdminReportsRepository()
        reports.revenueResult = ApiResult.Success(
            RevenueReport(totalRevenue = 750_000, totalOrders = 2, series = emptyList()),
        )
        reports.topProductsResult = failure("TOP_FAILED")
        val viewModel = viewModel(reports)
        advance()

        val state = viewModel.uiState.value
        assertFalse(state.isFullyFailed)
        assertTrue(state.topProducts is DashboardBlock.Failed)
        assertEquals(750_000L, state.kpis.revenue)
    }

    @Test
    fun `all blocks failing flags the whole screen error`() = runTest {
        val reports = FakeAdminReportsRepository()
        reports.revenueResult = failure("REVENUE_FAILED")
        reports.topProductsResult = failure("TOP_FAILED")
        reports.lowStockResult = failure("LOW_STOCK_FAILED")
        val orders = FakeAdminOrdersRepository(
            pages = listOf(ApiResult.Failure(ApiError(ApiErrorKind.NETWORK))),
        )
        val viewModel = viewModel(reports, orders)
        advance()

        val state = viewModel.uiState.value
        assertTrue(state.isFullyFailed)
        assertEquals("REVENUE_FAILED", state.firstError?.code)
    }

    @Test
    fun `a from date after to clamps both ends to that day`() = runTest {
        val reports = FakeAdminReportsRepository()
        val viewModel = viewModel(reports)
        advance()

        viewModel.onFromSelected("2026-11-10")
        advance()

        val state = viewModel.uiState.value
        assertEquals("2026-11-10", state.from)
        assertEquals("2026-11-10", state.to)
        assertEquals("2026-11-10" to "2026-11-10", reports.topProductsCalls.last())
    }

    @Test
    fun `a to date before from clamps both ends to that day`() = runTest {
        val reports = FakeAdminReportsRepository()
        val viewModel = viewModel(reports)
        advance()

        viewModel.onToSelected("2026-10-01")
        advance()

        val state = viewModel.uiState.value
        assertEquals("2026-10-01", state.from)
        assertEquals("2026-10-01", state.to)
    }

    @Test
    fun `dashboard chart keeps the latest seven points`() {
        val report = RevenueReport(
            totalRevenue = 100,
            totalOrders = 1,
            series = (1..9).map { day ->
                RevenuePoint(period = "2026-11-0$day", revenue = day.toLong(), orders = 1)
            },
        )

        assertEquals(
            listOf(
                "2026-11-03",
                "2026-11-04",
                "2026-11-05",
                "2026-11-06",
                "2026-11-07",
                "2026-11-08",
                "2026-11-09",
            ),
            dashboardChartSeries(report).map { it.period },
        )
    }

    @Test
    fun `kpis stay null while the blocks are loading`() {
        val kpis = AdminDashboardUiState(from = "2026-10-09", to = "2026-11-07").kpis

        assertEquals(null, kpis.revenue)
        assertEquals(null, kpis.orders)
        assertEquals(null, kpis.averageOrderValue)
        assertEquals(null, kpis.lowStockVariants)
    }
}
