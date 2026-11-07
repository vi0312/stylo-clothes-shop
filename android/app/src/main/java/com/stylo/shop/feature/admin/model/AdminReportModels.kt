package com.stylo.shop.feature.admin.model

import com.stylo.shop.feature.orders.model.OrderStatus
import java.time.ZoneId

val REPORT_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

enum class ReportGroupBy(val apiValue: String) {
    DAY("day"),
    MONTH("month"),
}

data class RevenuePoint(
    val period: String,
    val revenue: Long,
    val orders: Int,
)

data class RevenueReport(
    val totalRevenue: Long,
    val totalOrders: Int,
    val series: List<RevenuePoint>,
)

data class TopProductRow(
    val productId: Long,
    val name: String,
    val quantitySold: Int,
    val revenue: Long,
)

data class OrdersByStatusRow(
    val status: OrderStatus,
    val count: Int,
    val total: Long,
)

data class LowStockRow(
    val variantId: Long,
    val sku: String,
    val productName: String,
    val size: String,
    val color: String,
    val stock: Int,
)

data class InventoryCostReport(
    val receiptsCost: Long,
    val cogs: Long,
    val revenue: Long,
    val grossProfit: Long,
)
