package com.stylo.shop.e2e

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaspersky.components.composesupport.config.withComposeSupport
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.stylo.shop.MainActivity
import com.stylo.shop.core.ui.UiTags
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end customer journey against the real server (start it and `adb reverse tcp:8787` first).
 * Each run registers a fresh customer so it is repeatable on a seeded database.
 */
@RunWith(AndroidJUnit4::class)
class CustomerJourneyTest : TestCase(kaspressoBuilder = Kaspresso.Builder.withComposeSupport()) {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val auth by lazy { E2eAuthScreen(composeRule) }
    private val catalog by lazy { E2eCatalogScreen(composeRule) }
    private val detail by lazy { E2eProductDetailScreen(composeRule) }
    private val profile by lazy { E2eProfileScreen(composeRule) }
    private val addresses by lazy { E2eAddressListScreen(composeRule) }
    private val addressForm by lazy { E2eAddressFormScreen(composeRule) }

    private val email = "e2e+${System.currentTimeMillis()}@stylo.local"
    private val password = "E2ePass123!"
    private val fullName = "E2E Customer"
    private val phone = "0901234567"
    private val firstRecipient = "Nguyen E2E"
    private val secondRecipient = "Tran E2E"
    private val firstLine = "12 Le Loi, Quan 1"
    private val editedLine = "14 Le Loi, Quan 1"
    private val secondLine = "88 Hai Ba Trung, Quan 3"

    @Test
    fun registerBrowseCatalogAndManageAddresses() = run {
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
            catalog.profile.assertIsDisplayed()
        }

        step("Search, filter and sort the catalog") {
            catalog.search.performTextInput("thun")
            waitForCard("Áo thun basic")
            catalog.filter.performClick()
            waitForTag(UiTags.FilterApply)
            catalog.category("Áo").performClick()
            catalog.apply.performScrollTo()
            catalog.apply.performClick()
            waitForCard("Áo thun basic")
            catalog.sort.performClick()
            waitForText("Giá tăng dần")
            catalog.sortOption("Giá tăng dần").performClick()
            waitForCard("Áo thun basic")
        }

        step("Open product detail") {
            catalog.card("Áo thun basic").performClick()
            detail.name.assertIsDisplayed()
            detail.name.assertTextEquals("Áo thun basic")
            detail.price.assertIsDisplayed()
            detail.back.performClick()
            catalog.search.assertIsDisplayed()
        }

        step("Create the first address") {
            catalog.profile.performClick()
            waitForTag(UiTags.ProfileEmail)
            profile.email.assertIsDisplayed()
            profile.addresses.performScrollTo()
            profile.addresses.performClick()
            waitForTag(UiTags.AddressAdd)
            addresses.add.performClick()
            waitForTag(UiTags.AddressRecipient)
            addressForm.recipient.performTextInput(firstRecipient)
            addressForm.phone.performTextInput(phone)
            addressForm.line.performTextInput(firstLine)
            addressForm.save.performScrollTo()
            addressForm.save.performClick()
            waitForAddressHeader(firstRecipient)
            addresses.header(firstRecipient).assertIsDisplayed()
            composeRule.onNode(hasHeader(firstRecipient) and hasText("Mặc định")).assertExists()
        }

        step("Edit the first address") {
            addresses.edit.performClick()
            waitForTag(UiTags.AddressLine)
            addressForm.line.performTextReplacement(editedLine)
            addressForm.save.performScrollTo()
            addressForm.save.performClick()
            waitForText(editedLine)
        }

        step("Add a second address and make it the default") {
            waitForTag(UiTags.AddressAdd)
            addresses.add.performClick()
            waitForTag(UiTags.AddressRecipient)
            addressForm.recipient.performTextInput(secondRecipient)
            addressForm.phone.performTextInput(phone)
            addressForm.line.performTextInput(secondLine)
            addressForm.save.performScrollTo()
            addressForm.save.performClick()
            waitForAddressHeader(secondRecipient)
            addresses.setDefault.performScrollTo()
            addresses.setDefault.performClick()
            waitForDefault(secondRecipient)
            composeRule.onAllNodesWithText("Mặc định").assertCountEquals(1)
        }

        step("Log out and log back in") {
            addresses.back.performClick()
            waitForTag(UiTags.ProfileEmail)
            profile.email.assertIsDisplayed()
            profile.logout.performScrollTo()
            profile.logout.performClick()
            waitForTag(UiTags.ProfileLogoutConfirm)
            profile.confirmLogout.performClick()
            waitForTag(UiTags.AuthEmail)
            auth.email.performTextInput(email)
            auth.password.performTextInput(password)
            auth.submit.performScrollTo()
            auth.submit.performClick()
            waitForText("Xin chào, $fullName")
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

    private fun waitForAddressHeader(recipient: String) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasHeader(recipient)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForDefault(recipient: String) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasHeader(recipient) and hasText("Mặc định"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun hasHeader(recipient: String) =
        hasTestTag(UiTags.AddressHeader) and hasText(recipient)
}
