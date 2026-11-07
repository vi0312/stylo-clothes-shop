package com.stylo.shop.feature.orders

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.orders.data.OrdersRepository
import com.stylo.shop.feature.orders.model.Order
import com.stylo.shop.feature.orders.model.OrderItem
import com.stylo.shop.feature.orders.model.OrderPage
import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import com.stylo.shop.feature.orders.model.OrderStatus
import com.stylo.shop.feature.orders.model.OrderSummary
import com.stylo.shop.feature.orders.model.OrdersQuery
import com.stylo.shop.feature.orders.model.PaymentStatus

fun testOrderItem(
    id: Long = 501,
    productId: Long = 9,
    variantId: Long = 44,
    productName: String = "Áo thun cotton",
    sku: String = "AT-DEN-M",
    size: String = "M",
    color: String = "Đen",
    unitPrice: Long = 199_000,
    quantity: Int = 2,
): OrderItem = OrderItem(
    id = id,
    productId = productId,
    variantId = variantId,
    productName = productName,
    sku = sku,
    size = size,
    color = color,
    unitPrice = unitPrice,
    quantity = quantity,
    lineTotal = unitPrice * quantity,
)

fun testOrder(
    id: Long = 123,
    code: String = "STY-261104-A7K2",
    status: OrderStatus = OrderStatus.PENDING,
    paymentMethod: OrderPaymentMethod = OrderPaymentMethod.COD,
    paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    note: String? = null,
    items: List<OrderItem> = listOf(testOrderItem()),
    paidAt: String? = null,
): Order {
    val subtotal = items.sumOf { it.lineTotal }
    val shippingFee = 30_000L
    return Order(
        id = id,
        code = code,
        status = status,
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus,
        subtotal = subtotal,
        shippingFee = shippingFee,
        total = subtotal + shippingFee,
        note = note,
        recipientName = "Nguyễn Văn An",
        recipientPhone = "0901234567",
        shippingAddress = "12 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM",
        items = items,
        paidAt = paidAt,
        createdAt = "2026-11-04T03:20:00.000Z",
    )
}

fun testOrderSummary(
    id: Long = 123,
    code: String = "STY-261104-A7K2",
    status: OrderStatus = OrderStatus.PENDING,
    paymentMethod: OrderPaymentMethod = OrderPaymentMethod.COD,
    paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    total: Long = 428_000,
    itemCount: Int = 2,
): OrderSummary = OrderSummary(
    id = id,
    code = code,
    status = status,
    paymentMethod = paymentMethod,
    paymentStatus = paymentStatus,
    total = total,
    itemCount = itemCount,
    createdAt = "2026-11-04T03:20:00.000Z",
)

fun testOrderPage(
    items: List<OrderSummary> = listOf(testOrderSummary()),
    page: Int = 1,
    limit: Int = 20,
    total: Int = items.size,
): OrderPage = OrderPage(items = items, page = page, limit = limit, total = total)

class FakeOrdersRepository(
    var result: ApiResult<Order> = ApiResult.Success(testOrder()),
    var pages: List<ApiResult<OrderPage>> = listOf(ApiResult.Success(testOrderPage())),
    var cancelResult: ApiResult<Order>? = null,
) : OrdersRepository {

    val requestedIds = mutableListOf<Long>()
    val queries = mutableListOf<OrdersQuery>()
    val cancelledIds = mutableListOf<Long>()

    override suspend fun listOrders(query: OrdersQuery): ApiResult<OrderPage> {
        queries += query
        val index = (query.page - 1).coerceAtLeast(0)
        return pages.getOrElse(index) { pages.last() }
    }

    override suspend fun getOrder(orderId: Long): ApiResult<Order> {
        requestedIds += orderId
        return result
    }

    override suspend fun cancelOrder(orderId: Long): ApiResult<Order> {
        cancelledIds += orderId
        return cancelResult ?: result
    }
}
