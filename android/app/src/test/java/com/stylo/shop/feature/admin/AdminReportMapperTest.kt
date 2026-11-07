package com.stylo.shop.feature.admin

import com.stylo.shop.feature.admin.data.InventoryCostReportDto
import com.stylo.shop.feature.admin.data.LowStockItemDto
import com.stylo.shop.feature.admin.data.OrdersByStatusItemDto
import com.stylo.shop.feature.admin.data.RevenuePointDto
import com.stylo.shop.feature.admin.data.RevenueReportDto
import com.stylo.shop.feature.admin.data.TopProductDto
import com.stylo.shop.feature.admin.data.toModel
import com.stylo.shop.feature.orders.model.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminReportMapperTest {

    @Test
    fun `revenue maps totals and every zero filled period`() {
        val dto = RevenueReportDto(
            from = "2026-11-01",
            to = "2026-11-03",
            groupBy = "day",
            totalRevenue = 1_600_000,
            totalOrders = 5,
            series = listOf(
                RevenuePointDto("2026-11-01", 350_000, 2),
                RevenuePointDto("2026-11-02", 0, 0),
                RevenuePointDto("2026-11-03", 1_250_000, 3),
            ),
        )

        val model = dto.toModel()

        assertEquals(1_600_000L, model.totalRevenue)
        assertEquals(5, model.totalOrders)
        assertEquals(listOf("2026-11-01", "2026-11-02", "2026-11-03"), model.series.map { it.period })
        assertEquals(1_250_000L, model.series.last().revenue)
    }

    @Test
    fun `top products keep the server order and snapshot revenue`() {
        val dto = TopProductDto(productId = 9, name = "Áo thun cotton", quantitySold = 12, revenue = 2_388_000)

        val model = dto.toModel()

        assertEquals(9L, model.productId)
        assertEquals(12, model.quantitySold)
        assertEquals(2_388_000L, model.revenue)
    }

    @Test
    fun `orders by status maps the wire enum and falls back to pending`() {
        val known = OrdersByStatusItemDto(status = "DELIVERED", count = 30, total = 9_000_000).toModel()
        val unknown = OrdersByStatusItemDto(status = "SOMETHING_NEW", count = 1, total = 100).toModel()

        assertEquals(OrderStatus.DELIVERED, known.status)
        assertEquals(9_000_000L, known.total)
        assertEquals(OrderStatus.PENDING, unknown.status)
    }

    @Test
    fun `low stock maps the variant identity and stock`() {
        val dto = LowStockItemDto(
            variantId = 42,
            sku = "STY-AO-001-M",
            productName = "Áo thun cotton",
            size = "M",
            color = "Đen",
            stock = 1,
        )

        val model = dto.toModel()

        assertEquals(42L, model.variantId)
        assertEquals("STY-AO-001-M", model.sku)
        assertEquals(1, model.stock)
    }

    @Test
    fun `inventory cost keeps the four money lines`() {
        val dto = InventoryCostReportDto(
            from = "2026-10-01",
            to = "2026-10-31",
            receiptsCost = 5_000_000,
            cogs = 3_200_000,
            revenue = 6_000_000,
            grossProfit = 2_800_000,
        )

        val model = dto.toModel()

        assertEquals(5_000_000L, model.receiptsCost)
        assertEquals(3_200_000L, model.cogs)
        assertEquals(6_000_000L, model.revenue)
        assertEquals(2_800_000L, model.grossProfit)
    }
}
