package com.stylo.shop.feature.admin.data

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import com.stylo.shop.feature.admin.model.InventoryCostReport
import com.stylo.shop.feature.admin.model.LowStockRow
import com.stylo.shop.feature.admin.model.OrdersByStatusRow
import com.stylo.shop.feature.admin.model.ReportGroupBy
import com.stylo.shop.feature.admin.model.RevenuePoint
import com.stylo.shop.feature.admin.model.RevenueReport
import com.stylo.shop.feature.admin.model.TopProductRow
import com.stylo.shop.feature.orders.model.OrderStatus
import javax.inject.Inject

interface AdminReportsRepository {

    suspend fun revenue(
        from: String,
        to: String,
        groupBy: ReportGroupBy,
    ): ApiResult<RevenueReport>

    suspend fun topProducts(
        from: String,
        to: String,
        limit: Int = TOP_PRODUCTS_LIMIT,
    ): ApiResult<List<TopProductRow>>

    suspend fun ordersByStatus(from: String, to: String): ApiResult<List<OrdersByStatusRow>>

    suspend fun lowStock(threshold: Int): ApiResult<List<LowStockRow>>

    suspend fun inventoryCost(from: String, to: String): ApiResult<InventoryCostReport>
}

class DefaultAdminReportsRepository @Inject constructor(
    private val api: AdminReportsApi,
) : AdminReportsRepository {

    override suspend fun revenue(
        from: String,
        to: String,
        groupBy: ReportGroupBy,
    ): ApiResult<RevenueReport> = apiCall {
        api.revenue(from = from, to = to, groupBy = groupBy.apiValue).toModel()
    }

    override suspend fun topProducts(
        from: String,
        to: String,
        limit: Int,
    ): ApiResult<List<TopProductRow>> = apiCall {
        api.topProducts(from = from, to = to, limit = limit).items.map { it.toModel() }
    }

    override suspend fun ordersByStatus(
        from: String,
        to: String,
    ): ApiResult<List<OrdersByStatusRow>> = apiCall {
        api.ordersByStatus(from = from, to = to).items.map { it.toModel() }
    }

    override suspend fun lowStock(threshold: Int): ApiResult<List<LowStockRow>> = apiCall {
        api.lowStock(threshold = threshold).items.map { it.toModel() }
    }

    override suspend fun inventoryCost(
        from: String,
        to: String,
    ): ApiResult<InventoryCostReport> = apiCall {
        api.inventoryCost(from = from, to = to).toModel()
    }
}

fun RevenueReportDto.toModel(): RevenueReport = RevenueReport(
    totalRevenue = totalRevenue,
    totalOrders = totalOrders,
    series = series.map { RevenuePoint(period = it.period, revenue = it.revenue, orders = it.orders) },
)

fun TopProductDto.toModel(): TopProductRow = TopProductRow(
    productId = productId,
    name = name,
    quantitySold = quantitySold,
    revenue = revenue,
)

fun OrdersByStatusItemDto.toModel(): OrdersByStatusRow = OrdersByStatusRow(
    status = OrderStatus.fromApi(status),
    count = count,
    total = total,
)

fun LowStockItemDto.toModel(): LowStockRow = LowStockRow(
    variantId = variantId,
    sku = sku,
    productName = productName,
    size = size,
    color = color,
    stock = stock,
)

fun InventoryCostReportDto.toModel(): InventoryCostReport = InventoryCostReport(
    receiptsCost = receiptsCost,
    cogs = cogs,
    revenue = revenue,
    grossProfit = grossProfit,
)
