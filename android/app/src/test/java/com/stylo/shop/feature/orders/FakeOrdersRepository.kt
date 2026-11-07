package com.stylo.shop.feature.orders

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.orders.data.OrdersRepository
import com.stylo.shop.feature.orders.model.Order
import com.stylo.shop.feature.orders.model.OrderItem
import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import com.stylo.shop.feature.orders.model.OrderStatus
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

class FakeOrdersRepository(
    var result: ApiResult<Order> = ApiResult.Success(testOrder()),
) : OrdersRepository {

    val requestedIds = mutableListOf<Long>()

    override suspend fun getOrder(orderId: Long): ApiResult<Order> {
        requestedIds += orderId
        return result
    }
}
