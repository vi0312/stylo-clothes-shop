package com.stylo.shop.feature.admin.data

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import com.stylo.shop.feature.admin.model.AdminOrderCustomer
import com.stylo.shop.feature.admin.model.AdminOrderDetail
import com.stylo.shop.feature.admin.model.AdminOrderItem
import com.stylo.shop.feature.admin.model.AdminOrderQuery
import com.stylo.shop.feature.admin.model.AdminOrderSummary
import com.stylo.shop.feature.admin.model.AdminPayment
import com.stylo.shop.feature.admin.model.Paginated
import com.stylo.shop.feature.admin.model.PaymentAttemptStatus
import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import com.stylo.shop.feature.orders.model.OrderStatus
import com.stylo.shop.feature.orders.model.PaymentStatus
import javax.inject.Inject

interface AdminOrdersRepository {
    suspend fun listOrders(query: AdminOrderQuery): ApiResult<Paginated<AdminOrderSummary>>

    suspend fun getOrder(orderId: Long): ApiResult<AdminOrderDetail>

    suspend fun updateStatus(orderId: Long, target: OrderStatus): ApiResult<AdminOrderDetail>
}

class DefaultAdminOrdersRepository @Inject constructor(
    private val api: AdminOrdersApi,
) : AdminOrdersRepository {

    override suspend fun listOrders(
        query: AdminOrderQuery,
    ): ApiResult<Paginated<AdminOrderSummary>> = apiCall {
        api.listOrders(
            status = query.status?.apiValue,
            q = query.q,
            from = query.from,
            to = query.to,
            sort = query.sort,
            page = query.page,
            limit = query.limit,
        ).toPaginated()
    }

    override suspend fun getOrder(orderId: Long): ApiResult<AdminOrderDetail> = apiCall {
        api.getOrder(orderId).toAdminOrderDetail()
    }

    override suspend fun updateStatus(
        orderId: Long,
        target: OrderStatus,
    ): ApiResult<AdminOrderDetail> = apiCall {
        api.updateStatus(orderId, UpdateAdminOrderStatusRequest(target.apiValue))
            .toAdminOrderDetail()
    }
}

private fun AdminOrderPageDto.toPaginated(): Paginated<AdminOrderSummary> = Paginated(
    items = items.map { it.toSummary() },
    page = page,
    limit = limit,
    total = total,
)

private fun AdminOrderSummaryDto.toSummary(): AdminOrderSummary = AdminOrderSummary(
    id = id,
    code = code,
    status = OrderStatus.fromApi(status),
    paymentMethod = OrderPaymentMethod.fromApi(paymentMethod),
    paymentStatus = PaymentStatus.fromApi(paymentStatus),
    total = total,
    itemCount = itemCount,
    customerName = customerName,
    recipientName = recipientName,
    recipientPhone = recipientPhone,
    createdAt = createdAt,
)

fun AdminOrderDetailDto.toAdminOrderDetail(): AdminOrderDetail = AdminOrderDetail(
    id = id,
    code = code,
    status = OrderStatus.fromApi(status),
    paymentMethod = OrderPaymentMethod.fromApi(paymentMethod),
    paymentStatus = PaymentStatus.fromApi(paymentStatus),
    customer = AdminOrderCustomer(
        id = customer.id,
        email = customer.email,
        fullName = customer.fullName,
        phone = customer.phone,
    ),
    subtotal = subtotal,
    shippingFee = shippingFee,
    total = total,
    note = note,
    recipientName = recipientName,
    recipientPhone = recipientPhone,
    shippingAddress = shippingAddress,
    items = items.map {
        AdminOrderItem(
            id = it.id,
            productId = it.productId,
            variantId = it.variantId,
            productName = it.productName,
            sku = it.sku,
            size = it.size,
            color = it.color,
            unitPrice = it.unitPrice,
            unitCost = it.unitCost,
            quantity = it.quantity,
            lineTotal = it.lineTotal,
        )
    },
    payments = payments.map {
        AdminPayment(
            id = it.id,
            txnRef = it.txnRef,
            provider = it.provider,
            status = PaymentAttemptStatus.fromApi(it.status),
            amount = it.amount,
            failureReason = it.failureReason,
            createdAt = it.createdAt,
        )
    },
    paidAt = paidAt,
    createdAt = createdAt,
)
