package com.stylo.shop.feature.admin.model

import com.stylo.shop.core.network.ApiError

const val DASHBOARD_RANGE_DAYS = 30L
const val DASHBOARD_CHART_DAYS = 7
const val DASHBOARD_TOP_PRODUCTS = 3
const val DASHBOARD_RECENT_ORDERS = 3
const val DASHBOARD_LOW_STOCK_THRESHOLD = 5

/** Each dashboard block loads on its own so one failing endpoint cannot blank the whole screen. */
sealed interface DashboardBlock<out T> {
    data object Loading : DashboardBlock<Nothing>

    data class Ready<out T>(val value: T) : DashboardBlock<T>

    data class Failed(val error: ApiError) : DashboardBlock<Nothing>
}

val <T> DashboardBlock<T>.valueOrNull: T?
    get() = (this as? DashboardBlock.Ready)?.value

data class DashboardKpis(
    val revenue: Long?,
    val orders: Int?,
    val averageOrderValue: Long?,
    val lowStockVariants: Int?,
    val revenueDeltaPercent: Int? = null,
    val ordersDeltaPercent: Int? = null,
)

/**
 * Percent change against the previous window, rounded. Null when the baseline is missing or zero,
 * because "up 100%" from nothing is noise, not information.
 */
internal fun percentChange(previous: Long?, current: Long?): Int? {
    if (previous == null || current == null || previous <= 0L) return null
    return (((current - previous) * 100.0) / previous).toInt()
}

data class AdminDashboardUiState(
    val from: String,
    val to: String,
    val revenue: DashboardBlock<RevenueReport> = DashboardBlock.Loading,
    val previousRevenue: DashboardBlock<RevenueReport> = DashboardBlock.Loading,
    val topProducts: DashboardBlock<List<TopProductRow>> = DashboardBlock.Loading,
    val lowStock: DashboardBlock<List<LowStockRow>> = DashboardBlock.Loading,
    val recentOrders: DashboardBlock<List<AdminOrderSummary>> = DashboardBlock.Loading,
) {
    val kpis: DashboardKpis
        get() {
            val report = revenue.valueOrNull
            val previous = previousRevenue.valueOrNull
            return DashboardKpis(
                revenue = report?.totalRevenue,
                orders = report?.totalOrders,
                averageOrderValue = report?.let {
                    if (it.totalOrders == 0) 0L else it.totalRevenue / it.totalOrders
                },
                lowStockVariants = lowStock.valueOrNull?.size,
                revenueDeltaPercent = percentChange(previous?.totalRevenue, report?.totalRevenue),
                ordersDeltaPercent = percentChange(
                    previous?.totalOrders?.toLong(),
                    report?.totalOrders?.toLong(),
                ),
            )
        }

    val isLoading: Boolean
        get() = revenue is DashboardBlock.Loading &&
            topProducts is DashboardBlock.Loading &&
            lowStock is DashboardBlock.Loading &&
            recentOrders is DashboardBlock.Loading

    val isFullyFailed: Boolean
        get() = revenue is DashboardBlock.Failed &&
            topProducts is DashboardBlock.Failed &&
            lowStock is DashboardBlock.Failed &&
            recentOrders is DashboardBlock.Failed

    val firstError: ApiError?
        get() = listOfNotNull(
            (revenue as? DashboardBlock.Failed)?.error,
            (topProducts as? DashboardBlock.Failed)?.error,
            (lowStock as? DashboardBlock.Failed)?.error,
            (recentOrders as? DashboardBlock.Failed)?.error,
        ).firstOrNull()
}

/** The chart is always the latest seven days of the selected range. */
fun dashboardChartSeries(report: RevenueReport): List<RevenuePoint> =
    report.series.sortedBy { it.period }.takeLast(DASHBOARD_CHART_DAYS)
