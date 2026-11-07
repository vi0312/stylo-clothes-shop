package com.stylo.shop.feature.admin

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.admin.data.AdminReportsRepository
import com.stylo.shop.feature.admin.model.InventoryCostReport
import com.stylo.shop.feature.admin.model.LowStockRow
import com.stylo.shop.feature.admin.model.OrdersByStatusRow
import com.stylo.shop.feature.admin.model.ReportGroupBy
import com.stylo.shop.feature.admin.model.RevenueReport
import com.stylo.shop.feature.admin.model.TopProductRow

class FakeAdminReportsRepository : AdminReportsRepository {

    var revenueResult: ApiResult<RevenueReport> = ApiResult.Success(
        RevenueReport(totalRevenue = 0, totalOrders = 0, series = emptyList()),
    )
    var topProductsResult: ApiResult<List<TopProductRow>> = ApiResult.Success(emptyList())
    var ordersByStatusResult: ApiResult<List<OrdersByStatusRow>> = ApiResult.Success(emptyList())
    var lowStockResult: ApiResult<List<LowStockRow>> = ApiResult.Success(emptyList())
    var inventoryCostResult: ApiResult<InventoryCostReport> = ApiResult.Success(
        InventoryCostReport(receiptsCost = 0, cogs = 0, revenue = 0, grossProfit = 0),
    )

    val revenueCalls = mutableListOf<RevenueCall>()
    val topProductsCalls = mutableListOf<Pair<String, String>>()
    val topProductsLimits = mutableListOf<Int>()
    val ordersByStatusCalls = mutableListOf<Pair<String, String>>()
    val lowStockCalls = mutableListOf<Int>()
    val inventoryCostCalls = mutableListOf<Pair<String, String>>()

    override suspend fun revenue(
        from: String,
        to: String,
        groupBy: ReportGroupBy,
    ): ApiResult<RevenueReport> {
        revenueCalls += RevenueCall(from, to, groupBy)
        return revenueResult
    }

    override suspend fun topProducts(
        from: String,
        to: String,
        limit: Int,
    ): ApiResult<List<TopProductRow>> {
        topProductsCalls += from to to
        topProductsLimits += limit
        return topProductsResult
    }

    override suspend fun ordersByStatus(
        from: String,
        to: String,
    ): ApiResult<List<OrdersByStatusRow>> {
        ordersByStatusCalls += from to to
        return ordersByStatusResult
    }

    override suspend fun lowStock(threshold: Int): ApiResult<List<LowStockRow>> {
        lowStockCalls += threshold
        return lowStockResult
    }

    override suspend fun inventoryCost(
        from: String,
        to: String,
    ): ApiResult<InventoryCostReport> {
        inventoryCostCalls += from to to
        return inventoryCostResult
    }
}

data class RevenueCall(
    val from: String,
    val to: String,
    val groupBy: ReportGroupBy,
)
