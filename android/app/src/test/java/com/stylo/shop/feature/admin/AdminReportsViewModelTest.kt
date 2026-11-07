package com.stylo.shop.feature.admin

import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.model.InventoryCostReport
import com.stylo.shop.feature.admin.model.LowStockRow
import com.stylo.shop.feature.admin.model.REPORT_ZONE
import com.stylo.shop.feature.admin.model.ReportGroupBy
import com.stylo.shop.feature.admin.model.RevenuePoint
import com.stylo.shop.feature.admin.model.RevenueReport
import com.stylo.shop.feature.admin.model.TopProductRow
import com.stylo.shop.feature.admin.ui.reports.AdminReportContent
import com.stylo.shop.feature.admin.ui.reports.AdminReportKind
import com.stylo.shop.feature.admin.ui.reports.AdminReportsViewModel
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminReportsViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), REPORT_ZONE)

    private fun advance() = dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    private fun viewModel(repository: FakeAdminReportsRepository) =
        AdminReportsViewModel(repository, clock)

    private fun content(viewModel: AdminReportsViewModel): AdminReportContent =
        (viewModel.uiState.value.content as UiState.Content).data

    @Test
    fun `defaults to the last 30 Vietnam days of daily revenue`() = runTest {
        val repository = FakeAdminReportsRepository()
        val viewModel = viewModel(repository)
        advance()

        assertEquals(
            RevenueCall(from = "2026-10-09", to = "2026-11-07", groupBy = ReportGroupBy.DAY),
            repository.revenueCalls.single(),
        )
        assertEquals(AdminReportKind.REVENUE, viewModel.uiState.value.kind)
        assertTrue(content(viewModel) is AdminReportContent.Revenue)
    }

    @Test
    fun `selecting another report loads it with the shared range`() = runTest {
        val repository = FakeAdminReportsRepository()
        repository.topProductsResult = ApiResult.Success(
            listOf(
                TopProductRow(
                    productId = 4,
                    name = "Áo thun",
                    quantitySold = 3,
                    revenue = 360_000,
                ),
            ),
        )
        val viewModel = viewModel(repository)
        advance()

        viewModel.onSelectKind(AdminReportKind.TOP_PRODUCTS)
        advance()

        assertEquals("2026-10-09" to "2026-11-07", repository.topProductsCalls.single())
        assertEquals(
            listOf("Áo thun"),
            (content(viewModel) as AdminReportContent.TopProducts).items.map { it.name },
        )
    }

    @Test
    fun `the month grouping only touches the revenue report`() = runTest {
        val repository = FakeAdminReportsRepository()
        val viewModel = viewModel(repository)
        advance()

        viewModel.onSelectGroupBy(ReportGroupBy.MONTH)
        advance()

        assertEquals(2, repository.revenueCalls.size)
        assertEquals(ReportGroupBy.MONTH, repository.revenueCalls.last().groupBy)

        viewModel.onSelectKind(AdminReportKind.ORDERS_BY_STATUS)
        advance()
        viewModel.onSelectGroupBy(ReportGroupBy.DAY)
        advance()

        assertEquals(2, repository.revenueCalls.size)
    }

    @Test
    fun `a from date after to pulls the range end along`() = runTest {
        val repository = FakeAdminReportsRepository()
        val viewModel = viewModel(repository)
        advance()

        viewModel.onFromSelected("2026-12-01")
        advance()

        assertEquals("2026-12-01" to "2026-12-01", repository.revenueCalls.last().let { it.from to it.to })
    }

    @Test
    fun `a failed report can be retried`() = runTest {
        val repository = FakeAdminReportsRepository()
        repository.inventoryCostResult = ApiResult.Failure(ApiError(ApiErrorKind.NETWORK))
        val viewModel = viewModel(repository)
        advance()

        viewModel.onSelectKind(AdminReportKind.INVENTORY_COST)
        advance()
        assertTrue(viewModel.uiState.value.content is UiState.Error)

        repository.inventoryCostResult = ApiResult.Success(
            InventoryCostReport(receiptsCost = 1_000, cogs = 400, revenue = 900, grossProfit = 500),
        )
        viewModel.retry()
        advance()

        assertEquals(
            500L,
            (content(viewModel) as AdminReportContent.InventoryCost).report.grossProfit,
        )
    }

    @Test
    fun `the low stock threshold is clamped and reloads the snapshot`() = runTest {
        val repository = FakeAdminReportsRepository()
        repository.lowStockResult = ApiResult.Success(
            listOf(
                LowStockRow(
                    variantId = 11,
                    sku = "STY-AO-001-M",
                    productName = "Áo thun cotton",
                    size = "M",
                    color = "Đen",
                    stock = 2,
                ),
            ),
        )
        val viewModel = viewModel(repository)
        advance()
        viewModel.onSelectKind(AdminReportKind.LOW_STOCK)
        advance()

        viewModel.onThresholdInputChange("9999")
        viewModel.onThresholdSubmit()
        advance()

        assertEquals(listOf(5, 1000), repository.lowStockCalls)
        assertEquals(
            1000,
            (content(viewModel) as AdminReportContent.LowStock).threshold,
        )
    }

    @Test
    fun `the revenue content keeps the zero filled series`() = runTest {
        val repository = FakeAdminReportsRepository()
        repository.revenueResult = ApiResult.Success(
            RevenueReport(
                totalRevenue = 350_000,
                totalOrders = 2,
                series = listOf(
                    RevenuePoint("2026-11-06", 0, 0),
                    RevenuePoint("2026-11-07", 350_000, 2),
                ),
            ),
        )
        val viewModel = viewModel(repository)
        advance()

        val report = (content(viewModel) as AdminReportContent.Revenue).report
        assertEquals(listOf("2026-11-06", "2026-11-07"), report.series.map { it.period })
    }
}
