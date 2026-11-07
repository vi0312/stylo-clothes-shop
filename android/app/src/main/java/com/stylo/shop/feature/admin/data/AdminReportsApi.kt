package com.stylo.shop.feature.admin.data

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface AdminReportsApi {

    @GET("admin/reports/revenue")
    suspend fun revenue(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("groupBy") groupBy: String,
    ): RevenueReportDto

    @GET("admin/reports/top-products")
    suspend fun topProducts(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("limit") limit: Int = TOP_PRODUCTS_LIMIT,
    ): TopProductsReportDto

    @GET("admin/reports/orders-by-status")
    suspend fun ordersByStatus(
        @Query("from") from: String,
        @Query("to") to: String,
    ): OrdersByStatusReportDto

    @GET("admin/reports/low-stock")
    suspend fun lowStock(@Query("threshold") threshold: Int): LowStockReportDto

    @GET("admin/reports/inventory-cost")
    suspend fun inventoryCost(
        @Query("from") from: String,
        @Query("to") to: String,
    ): InventoryCostReportDto
}

const val TOP_PRODUCTS_LIMIT = 10

@Serializable
data class RevenueReportDto(
    val from: String,
    val to: String,
    val groupBy: String,
    val totalRevenue: Long,
    val totalOrders: Int,
    val series: List<RevenuePointDto> = emptyList(),
)

@Serializable
data class RevenuePointDto(
    val period: String,
    val revenue: Long,
    val orders: Int,
)

@Serializable
data class TopProductsReportDto(
    val from: String,
    val to: String,
    val items: List<TopProductDto> = emptyList(),
)

@Serializable
data class TopProductDto(
    val productId: Long,
    val name: String,
    val quantitySold: Int,
    val revenue: Long,
)

@Serializable
data class OrdersByStatusReportDto(
    val from: String,
    val to: String,
    val items: List<OrdersByStatusItemDto> = emptyList(),
)

@Serializable
data class OrdersByStatusItemDto(
    val status: String,
    val count: Int,
    val total: Long,
)

@Serializable
data class LowStockReportDto(
    val threshold: Int,
    val items: List<LowStockItemDto> = emptyList(),
)

@Serializable
data class LowStockItemDto(
    val variantId: Long,
    val sku: String,
    val productName: String,
    val size: String,
    val color: String,
    val stock: Int,
)

@Serializable
data class InventoryCostReportDto(
    val from: String,
    val to: String,
    val receiptsCost: Long,
    val cogs: Long,
    val revenue: Long,
    val grossProfit: Long,
)
