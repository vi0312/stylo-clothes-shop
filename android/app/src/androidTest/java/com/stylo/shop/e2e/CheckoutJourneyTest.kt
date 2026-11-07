package com.stylo.shop.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * Checkout journey against the real server: cart -> checkout -> saved address -> COD order ->
 * order detail. Needs the API on `127.0.0.1:8787` and `adb reverse tcp:8787 tcp:8787`.
 *
 * Each run registers a fresh customer, so it stays repeatable on a seeded database; it also
 * consumes one unit of the first in-stock "Áo thun basic" variant, so the dev stock has to be
 * topped up before a long series of runs.
 */
@RunWith(AndroidJUnit4::class)
class CheckoutJourneyTest : TestCase(kaspressoBuilder = Kaspresso.Builder.withComposeSupport()) {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val auth by lazy { E2eAuthScreen(composeRule) }
    private val catalog by lazy { E2eCatalogScreen(composeRule) }
    private val detail by lazy { E2eProductDetailScreen(composeRule) }
    private val cart by lazy { E2eCartScreen(composeRule) }
    private val checkout by lazy { E2eCheckoutScreen(composeRule) }
    private val orderDetail by lazy { E2eOrderDetailScreen(composeRule) }
    private val addressForm by lazy { E2eAddressFormScreen(composeRule) }

    private val email = "checkout+${System.currentTimeMillis()}@stylo.local"
    private val password = "E2ePass123!"
    private val fullName = "E2E Checkout"
    private val phone = "0901234567"
    private val recipient = "Le E2E"
    private val line = "45 Nguyen Hue, P. Ben Nghe, Q.1, TP.HCM"

    @Test
    fun placesACodOrderFromTheCart() = run {
        step("Start logged out") {
            composeRule.startLoggedOut()
        }

        step("Register a new customer") {
            waitForTag(UiTags.AuthRegisterTab)
            auth.registerTab.performClick()
            auth.fullName.performTextInput(fullName)
            auth.phone.performTextInput(phone)
            auth.email.performTextInput(email)
            auth.password.performTextInput(password)
            auth.submit.performScrollTo()
            auth.submit.performClick()
            waitForText("Xin chào, $fullName")
        }

        step("Add a product to the cart") {
            waitForCard("Áo thun basic")
            catalog.card("Áo thun basic").performClick()
            waitForTag(UiTags.ProductDetailCta)
            detail.cta.performClick()
            waitForText("Đã thêm vào giỏ.")
        }

        step("Open the cart and continue to checkout") {
            detail.back.performClick()
            waitForTag(UiTags.CatalogCart)
            catalog.cart.performClick()
            waitForTag(UiTags.CartCheckout)
            cart.itemName.assertIsDisplayed()
            cart.checkout.performClick()
            waitForTag(UiTags.CheckoutPaymentCod)
        }

        step("Add the shipping address from the empty state") {
            // Two buttons carry this tag while no address exists: the empty state and the bar.
            composeRule.onAllNodesWithTag(UiTags.CheckoutAddressAdd).onFirst().performClick()
            waitForTag(UiTags.AddressRecipient)
            addressForm.recipient.performTextInput(recipient)
            addressForm.phone.performTextInput(phone)
            addressForm.line.performTextInput(line)
            addressForm.save.performScrollTo()
            addressForm.save.performClick()
            waitForTag(UiTags.CheckoutAddressChange)
        }

        step("Place the COD order") {
            checkout.paymentCod.performClick()
            checkout.submit.performClick()
            waitForTag(UiTags.OrderDetailCode)
            composeRule.onNodeWithTag(UiTags.OrderDetailCode).assertIsDisplayed()
            orderDetail.total.assertIsDisplayed()
            val statusShown = composeRule.onAllNodesWithText("Chờ xác nhận")
                .fetchSemanticsNodes().isNotEmpty()
            assertTrue("order detail shows the pending status", statusShown)
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForCard(name: String) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasTestTag(UiTags.ProductCard) and hasText(name))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
