package com.stylo.shop.feature.orders.data

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import com.stylo.shop.feature.orders.model.Order
import com.stylo.shop.feature.orders.model.OrderItem
import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import com.stylo.shop.feature.orders.model.OrderStatus
import com.stylo.shop.feature.orders.model.PaymentStatus
import javax.inject.Inject

interface OrdersRepository {
    suspend fun getOrder(orderId: Long): ApiResult<Order>
}

class DefaultOrdersRepository @Inject constructor(
    private val api: OrdersApi,
) : OrdersRepository {

    override suspend fun getOrder(orderId: Long): ApiResult<Order> = apiCall {
        api.getOrder(orderId).toOrder()
    }
}

fun OrderDto.toOrder(): Order = Order(
    id = id,
    code = code,
    status = OrderStatus.fromApi(status),
    paymentMethod = OrderPaymentMethod.fromApi(paymentMethod),
    paymentStatus = PaymentStatus.fromApi(paymentStatus),
    subtotal = subtotal,
    shippingFee = shippingFee,
    total = total,
    note = note,
    recipientName = recipientName,
    recipientPhone = recipientPhone,
    shippingAddress = shippingAddress,
    items = items.map { it.toOrderItem() },
    paidAt = paidAt,
    createdAt = createdAt,
)

private fun OrderItemDto.toOrderItem(): OrderItem = OrderItem(
    id = id,
    productId = productId,
    variantId = variantId,
    productName = productName,
    sku = sku,
    size = size,
    color = color,
    unitPrice = unitPrice,
    quantity = quantity,
    lineTotal = lineTotal,
)
