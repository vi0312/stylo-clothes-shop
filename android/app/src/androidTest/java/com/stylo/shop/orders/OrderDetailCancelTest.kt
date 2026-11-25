package com.stylo.shop.orders

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.core.ui.theme.StyloTheme
import com.stylo.shop.feature.orders.data.OrdersRepository
import com.stylo.shop.feature.orders.model.Order
import com.stylo.shop.feature.orders.model.OrderItem
import com.stylo.shop.feature.orders.model.OrderPage
import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import com.stylo.shop.feature.orders.model.OrderStatus
import com.stylo.shop.feature.orders.model.OrdersQuery
import com.stylo.shop.feature.orders.model.PaymentStatus
import com.stylo.shop.feature.orders.ui.OrderDetailScreen
import com.stylo.shop.feature.orders.ui.OrderDetailViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the paid-online cancellation contract: the reason is mandatory, the refund notice is
 * visible before and after cancelling, and the cancelled order keeps both the reason and the
 * payment snapshot. No server is involved.
 */
@RunWith(AndroidJUnit4::class)
class OrderDetailCancelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun paidOnlineCancelRequiresReasonAndShowsRefundNotice() {
        val repository = FakeOrdersRepository(onlinePaidOrder())
        val viewModel = OrderDetailViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf("orderId" to 123L)),
        )

        composeRule.setContent {
            StyloTheme {
                OrderDetailScreen(onBack = {}, viewModel = viewModel)
            }
        }

        composeRule.waitForTag(UiTags.OrderDetailCode)
        composeRule.onNodeWithTag(UiTags.OrderDetailList)
            .performScrollToNode(hasTestTag(UiTags.OrderCancel))
        composeRule.onNodeWithTag(UiTags.OrderCancel).performClick()

        composeRule.waitForTag(UiTags.OrderCancelRefundNotice)
        composeRule.onNodeWithTag(UiTags.OrderCancelReason).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTags.OrderCancelConfirm).assertIsNotEnabled()

        composeRule.onNodeWithTag(UiTags.OrderCancelReason).performTextInput("Đổi ý")
        composeRule.onNodeWithTag(UiTags.OrderCancelConfirm).assertIsEnabled().performClick()

        composeRule.waitUntil(5_000) { repository.cancelReason == "Đổi ý" }
        assertEquals("Đổi ý", repository.cancelReason)

        composeRule.waitForText("Đổi ý")
        composeRule.onNodeWithTag(UiTags.OrderCancelRefundNotice).assertIsDisplayed()
    }

    private fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = 5_000) {
        waitUntil(timeoutMs) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = 5_000) {
        waitUntil(timeoutMs) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

private class FakeOrdersRepository(private var order: Order) : OrdersRepository {

    var cancelReason: String? = null

    override suspend fun listOrders(query: OrdersQuery): ApiResult<OrderPage> =
        ApiResult.Success(OrderPage(items = emptyList(), page = 1, limit = 20, total = 0))

    override suspend fun getOrder(orderId: Long): ApiResult<Order> = ApiResult.Success(order)

    override suspend fun cancelOrder(orderId: Long, reason: String): ApiResult<Order> {
        cancelReason = reason
        order = order.copy(status = OrderStatus.CANCELLED, cancelReason = reason)
        return ApiResult.Success(order)
    }
}

private fun onlinePaidOrder(): Order {
    val item = OrderItem(
        id = 501,
        productId = 9,
        variantId = 44,
        productName = "Áo thun cotton",
        sku = "AT-DEN-M",
        size = "M",
        color = "Đen",
        unitPrice = 199_000,
        quantity = 2,
        lineTotal = 398_000,
    )
    return Order(
        id = 123,
        code = "STY-261104-A7K2",
        status = OrderStatus.CONFIRMED,
        paymentMethod = OrderPaymentMethod.ONLINE,
        paymentStatus = PaymentStatus.PAID,
        subtotal = 398_000,
        shippingFee = 30_000,
        total = 428_000,
        note = null,
        cancelReason = null,
        recipientName = "Nguyễn Văn An",
        recipientPhone = "0901234567",
        shippingAddress = "12 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM",
        items = listOf(item),
        paidAt = "2026-11-04T03:21:12.000Z",
        createdAt = "2026-11-04T03:20:00.000Z",
    )
}
