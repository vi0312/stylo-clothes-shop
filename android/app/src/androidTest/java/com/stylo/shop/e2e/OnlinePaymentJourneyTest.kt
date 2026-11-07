package com.stylo.shop.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaspersky.components.composesupport.config.withComposeSupport
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.stylo.shop.MainActivity
import com.stylo.shop.core.ui.UiTags
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ONLINE checkout up to the payment hand-off, then the app's return path: the browser intent is
 * stubbed so no Chrome is involved, the test completes the mock gateway call the page would post,
 * and the order detail must flip to PAID. Driving Chrome itself stays in the manual smoke.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class OnlinePaymentJourneyTest : TestCase(kaspressoBuilder = Kaspresso.Builder.withComposeSupport()) {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val auth by lazy { E2eAuthScreen(composeRule) }
    private val catalog by lazy { E2eCatalogScreen(composeRule) }
    private val detail by lazy { E2eProductDetailScreen(composeRule) }
    private val cart by lazy { E2eCartScreen(composeRule) }
    private val checkout by lazy { E2eCheckoutScreen(composeRule) }
    private val orderDetail by lazy { E2eOrderDetailScreen(composeRule) }
    private val addressForm by lazy { E2eAddressFormScreen(composeRule) }

    private val email = "e2e+online+${System.currentTimeMillis()}@stylo.local"
    private val password = "E2ePass123!"
    private val fullName = "E2E Online"
    private val phone = "0901234567"
    private val recipient = "Nguyen Online"
    private val line = "12 Le Loi, Quan 1"

    @Before
    fun stubBrowserHandOff() {
        Intents.init()
        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
    }

    @After
    fun releaseIntents() {
        Intents.release()
    }

    @Test
    fun onlineOrderBecomesPaidAfterTheMockGatewayCompletes() = run {
        step("Start logged out") {
            composeRule.startLoggedOut()
        }

        step("Register a new customer") {
            composeRule.registerCustomer(auth, fullName, phone, email, password)
        }

        step("Place an ONLINE order with a saved address") {
            composeRule.waitForCard("Áo thun basic")
            catalog.card("Áo thun basic").performClick()
            composeRule.waitForTag(UiTags.ProductDetailCta)
            detail.cta.performClick()
            composeRule.waitForText("Đã thêm vào giỏ.")
            detail.back.performClick()

            composeRule.waitForTag(UiTags.CatalogCart)
            catalog.cart.performClick()
            composeRule.waitForTag(UiTags.CartCheckout)
            cart.checkout.performClick()
            composeRule.waitForTag(UiTags.CheckoutPaymentCod)

            composeRule.onAllNodesWithTag(UiTags.CheckoutAddressAdd).onFirst().performClick()
            composeRule.waitForTag(UiTags.AddressRecipient)
            addressForm.recipient.performTextInput(recipient)
            addressForm.phone.performTextInput(phone)
            addressForm.line.performTextInput(line)
            addressForm.save.performScrollTo()
            addressForm.save.performClick()
            composeRule.waitForTag(UiTags.CheckoutAddressChange)

            checkout.paymentOnline.performClick()
            checkout.submit.performClick()
            composeRule.waitForTag(UiTags.OrderDetailCode)
        }

        step("Complete the mock payment server-side") {
            val paymentUrl = waitForValue(10_000) {
                Intents.getIntents()
                    .lastOrNull { it.action == Intent.ACTION_VIEW && it.data?.path?.contains("/payments/mock/") == true }
                    ?.data
                    ?.toString()
            }
            assertTrue("app opened the mock gateway: $paymentUrl", paymentUrl.contains("/payments/mock/"))
            completeMockPayment(paymentUrl)
        }

        step("The order detail reports the payment") {
            composeRule.waitForTag(UiTags.OrderDetailPaymentRetry)
            orderDetail.list.performScrollToNode(hasTestTag(UiTags.OrderDetailPaymentRetry))
            orderDetail.paymentRefresh.performClick()
            composeRule.waitForText("Đã thanh toán")
        }
    }

    private fun completeMockPayment(paymentUrl: String) {
        val connection = (URL("$paymentUrl/complete").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            connection.outputStream.use { it.write("result=success".toByteArray()) }
            val status = connection.responseCode
            assertTrue("mock payment completion returned $status", status in 200..399)
        } finally {
            connection.disconnect()
        }
    }
}
