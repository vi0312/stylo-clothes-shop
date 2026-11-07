package com.stylo.shop.feature.admin

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.admin.data.AdminOrdersRepository
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

fun testAdminOrderItem(
    id: Long = 501,
    productName: String = "Áo thun cotton",
    sku: String = "AT-DEN-M",
    unitPrice: Long = 199_000,
    unitCost: Long? = 120_000,
    quantity: Int = 2,
): AdminOrderItem = AdminOrderItem(
    id = id,
    productId = 9,
    variantId = 44,
    productName = productName,
    sku = sku,
    size = "M",
    color = "Đen",
    unitPrice = unitPrice,
    unitCost = unitCost,
    quantity = quantity,
    lineTotal = unitPrice * quantity,
)

fun testAdminPayment(
    id: Long = 900,
    txnRef: String = "STY261104032000123",
    status: PaymentAttemptStatus = PaymentAttemptStatus.PENDING,
    failureReason: String? = null,
): AdminPayment = AdminPayment(
    id = id,
    txnRef = txnRef,
    provider = "MOCK",
    status = status,
    amount = 428_000,
    failureReason = failureReason,
    createdAt = "2026-11-04T03:20:00.000Z",
)

fun testAdminOrderDetail(
    id: Long = 123,
    code: String = "STY-261104-A7K2",
    status: OrderStatus = OrderStatus.PENDING,
    paymentMethod: OrderPaymentMethod = OrderPaymentMethod.COD,
    paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    paidAt: String? = null,
    items: List<AdminOrderItem> = listOf(testAdminOrderItem()),
    payments: List<AdminPayment> = emptyList(),
    customerName: String = "Nguyễn Văn An",
): AdminOrderDetail {
    val subtotal = items.sumOf { it.lineTotal }
    return AdminOrderDetail(
        id = id,
        code = code,
        status = status,
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus,
        customer = AdminOrderCustomer(
            id = 7,
            email = "customer01@stylo.local",
            fullName = customerName,
            phone = "0901234567",
        ),
        subtotal = subtotal,
        shippingFee = 30_000,
        total = subtotal + 30_000,
        note = null,
        recipientName = customerName,
        recipientPhone = "0901234567",
        shippingAddress = "12 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM",
        items = items,
        payments = payments,
        paidAt = paidAt,
        createdAt = "2026-11-04T03:20:00.000Z",
    )
}

fun testAdminOrderSummary(
    id: Long = 123,
    code: String = "STY-261104-A7K2",
    status: OrderStatus = OrderStatus.PENDING,
    customerName: String = "Nguyễn Văn An",
    total: Long = 428_000,
    itemCount: Int = 2,
): AdminOrderSummary = AdminOrderSummary(
    id = id,
    code = code,
    status = status,
    paymentMethod = OrderPaymentMethod.COD,
    paymentStatus = PaymentStatus.UNPAID,
    total = total,
    itemCount = itemCount,
    customerName = customerName,
    recipientName = customerName,
    recipientPhone = "0901234567",
    createdAt = "2026-11-04T03:20:00.000Z",
)

class FakeAdminOrdersRepository(
    var pages: List<ApiResult<Paginated<AdminOrderSummary>>> = listOf(
        ApiResult.Success(
            Paginated(items = listOf(testAdminOrderSummary()), page = 1, limit = 20, total = 1),
        ),
    ),
    var detail: ApiResult<AdminOrderDetail> = ApiResult.Success(testAdminOrderDetail()),
    var updateResult: ApiResult<AdminOrderDetail>? = null,
) : AdminOrdersRepository {

    val queries = mutableListOf<AdminOrderQuery>()
    val requestedIds = mutableListOf<Long>()
    val updates = mutableListOf<Pair<Long, OrderStatus>>()

    override suspend fun listOrders(
        query: AdminOrderQuery,
    ): ApiResult<Paginated<AdminOrderSummary>> {
        queries += query
        val index = (query.page - 1).coerceAtLeast(0)
        return pages.getOrElse(index) { pages.last() }
    }

    override suspend fun getOrder(orderId: Long): ApiResult<AdminOrderDetail> {
        requestedIds += orderId
        return detail
    }

    override suspend fun updateStatus(
        orderId: Long,
        target: OrderStatus,
    ): ApiResult<AdminOrderDetail> {
        updates += orderId to target
        return updateResult ?: detail
    }
}
