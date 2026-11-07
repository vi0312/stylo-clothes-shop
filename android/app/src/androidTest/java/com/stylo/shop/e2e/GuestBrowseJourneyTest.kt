package com.stylo.shop.e2e

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kaspersky.components.composesupport.config.withComposeSupport
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.stylo.shop.MainActivity
import com.stylo.shop.core.ui.UiTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The guest shell (docs/ui.md §Điều hướng): browse Home/Catalog without a session, get Login from
 * every guarded tab, and come back to that tab after signing in. Uses the seeded customer so the
 * assertion does not depend on a registration this test did not perform.
 */
@RunWith(AndroidJUnit4::class)
class GuestBrowseJourneyTest : TestCase(kaspressoBuilder = Kaspresso.Builder.withComposeSupport()) {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val auth by lazy { E2eAuthScreen(composeRule) }
    private val nav by lazy { E2eNavBar(composeRule) }
    private val catalog by lazy { E2eCatalogScreen(composeRule) }
    private val detail by lazy { E2eProductDetailScreen(composeRule) }

    private val seedEmail = "customer01@stylo.local"
    private val seedPassword = "Customer@12345"
    private val seedName = "Nguyễn Văn An"

    @Test
    fun guestBrowsesHomeAndGuardedTabsReturnAfterLogin() = run {
        step("Guest lands on Home without a session") {
            composeRule.startGuest()
            nav.home.assertIsDisplayed()
            composeRule.onAllNodesWithTag(UiTags.HomeGreeting).assertCountEquals(0)
            composeRule.waitForTag(UiTags.ProductCard)
        }

        step("Cart, Orders and Profile all ask for a login") {
            nav.orders.performClick()
            composeRule.waitForTag(UiTags.AuthEmail)
            composeRule.onAllNodesWithTag(UiTags.NavHome).assertCountEquals(0)
            pressBack()
            composeRule.waitForTag(UiTags.NavHome)

            nav.profile.performClick()
            composeRule.waitForTag(UiTags.AuthEmail)
            pressBack()
            composeRule.waitForTag(UiTags.NavHome)

            nav.cart.performClick()
            composeRule.waitForTag(UiTags.AuthEmail)
        }

        step("Login returns to the tab that asked for it") {
            auth.email.performTextInput(seedEmail)
            auth.password.performTextInput(seedPassword)
            auth.submit.performScrollTo()
            auth.submit.performClick()
            composeRule.waitForTag(UiTags.CartRoot)
            composeRule.onNodeWithTag(UiTags.NavCart).assertIsSelected()
        }

        step("Back from a pushed screen keeps the root tab") {
            nav.home.performClick()
            composeRule.waitForText("Xin chào, $seedName")
            nav.catalog.performClick()
            composeRule.waitForTag(UiTags.CatalogSearch)
            composeRule.searchCatalog("thun")
            composeRule.waitForCard("Áo thun basic")
            catalog.card("Áo thun basic").performClick()
            composeRule.waitForTag(UiTags.ProductDetailName)
            detail.back.performClick()
            composeRule.waitForTag(UiTags.CatalogSearch)
            composeRule.onNodeWithTag(UiTags.NavCatalog).assertIsSelected()
        }
    }

    private fun pressBack() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
    }
}
