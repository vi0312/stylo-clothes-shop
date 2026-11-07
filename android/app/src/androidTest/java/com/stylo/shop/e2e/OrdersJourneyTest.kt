package com.stylo.shop.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaspersky.components.composesupport.config.withComposeSupport
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.stylo.shop.MainActivity
import com.stylo.shop.core.ui.UiTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Orders journey against the real server: a new COD order shows up in the customer's list, opens
 * from there, and cancels into the CANCELLED filter. Needs the API on `127.0.0.1:8787` and
 * `adb reverse tcp:8787 tcp:8787`.
 *
 * The order is created through the UI because a freshly registered customer owns no orders and
 * the suite does not use seeded accounts. Ticket 39 changes the profile entry for orders; this
 * journey follows it.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class OrdersJourneyTest : TestCase(kaspressoBuilder = Kaspresso.Builder.withComposeSupport()) {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val auth by lazy { E2eAuthScreen(composeRule) }
    private val nav by lazy { E2eNavBar(composeRule) }
    private val catalog by lazy { E2eCatalogScreen(composeRule) }
    private val detail by lazy { E2eProductDetailScreen(composeRule) }
    private val cart by lazy { E2eCartScreen(composeRule) }
    private val checkout by lazy { E2eCheckoutScreen(composeRule) }
    private val orderDetail by lazy { E2eOrderDetailScreen(composeRule) }
    private val orders by lazy { E2eOrdersScreen(composeRule) }
    private val addressForm by lazy { E2eAddressFormScreen(composeRule) }

    private val email = "e2e+orders+${System.currentTimeMillis()}@stylo.local"
    private val password = "E2ePass123!"
    private val fullName = "E2E Orders"
    private val phone = "0901234567"
    private val recipient = "Nguyen Orders"
    private val line = "12 Le Loi, Quan 1"

    @Test
    fun orderListOpensAndCancelsAPendingOrder() = run {
        step("Start logged out") {
            composeRule.startLoggedOut()
        }

        step("Register a new customer") {
            composeRule.registerCustomer(auth, fullName, phone, email, password)
        }

        step("Place a COD order") {
            composeRule.openCatalog()
            composeRule.searchCatalog("thun")
            composeRule.waitForCard("Áo thun basic")
            catalog.card("Áo thun basic").performClick()
            composeRule.waitForTag(UiTags.ProductDetailCta)
            detail.cta.performClick()
            composeRule.waitForText("Đã thêm vào giỏ.")
            detail.back.performClick()

            composeRule.waitForTag(UiTags.NavCart)
            nav.cart.performClick()
            composeRule.waitForTag(UiTags.CartCheckout)
            cart.checkout.performClick()
            composeRule.waitForTag(UiTags.CheckoutPaymentCod)

            // Two buttons carry this tag while no address exists: the empty state and the bar.
            composeRule.onAllNodesWithTag(UiTags.CheckoutAddressAdd).onFirst().performClick()
            composeRule.waitForTag(UiTags.AddressRecipient)
            addressForm.recipient.performTextInput(recipient)
            addressForm.phone.performTextInput(phone)
            addressForm.line.performTextInput(line)
            addressForm.save.performScrollTo()
            addressForm.save.performClick()
            composeRule.waitForTag(UiTags.CheckoutAddressChange)

            checkout.paymentCod.performClick()
            checkout.submit.performClick()
            composeRule.waitForTag(UiTags.OrderDetailCode)
            orderDetail.total.assertIsDisplayed()
        }

        val orderCode = composeRule.onNodeWithTag(UiTags.OrderDetailCode)
            .fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .joinToString("") { it.text }
        assertTrue("order detail shows a code", orderCode.isNotBlank())

        step("Reach the orders list from its tab") {
            // Checkout replaced cart in the back stack, so back from the order lands on Home.
            orderDetail.back.performClick()
            composeRule.waitForTag(UiTags.NavOrders)
            nav.orders.performClick()
            composeRule.waitForTag(UiTags.OrdersList)
            orders.row(orderCode).assertIsDisplayed()
        }

        step("Open the order from the list") {
            orders.row(orderCode).performClick()
            composeRule.waitForTag(UiTags.OrderDetailCode)
            orderDetail.code.assertIsDisplayed()
        }

        step("Cancel the pending order") {
            // The cancel block is a lazy item, so it is not composed until the list scrolls to it.
            orderDetail.list.performScrollToNode(hasTestTag(UiTags.OrderCancel))
            orderDetail.cancel.performClick()
            composeRule.waitForTag(UiTags.OrderCancelConfirm)
            orderDetail.cancelConfirm.performClick()
            composeRule.waitForText("Đã huỷ")
        }

        step("The cancelled order stays visible under its filter") {
            orderDetail.back.performClick()
            composeRule.waitForTag(UiTags.OrdersList)
            orders.filters.performScrollToNode(
                hasTestTag(UiTags.OrdersStatusFilter) and hasText("Đã huỷ"),
            )
            orders.statusFilter("Đã huỷ").performClick()
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithTag(UiTags.OrderRow)
                    .fetchSemanticsNodes()
                    .any { node ->
                        node.config.getOrNull(SemanticsProperties.Text)
                            .orEmpty()
                            .any { it.text == orderCode }
                    }
            }
            orders.row(orderCode).assertIsDisplayed()
        }
    }
}
