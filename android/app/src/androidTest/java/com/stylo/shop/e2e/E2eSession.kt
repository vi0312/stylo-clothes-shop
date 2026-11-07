package com.stylo.shop.e2e

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.stylo.shop.core.ui.UiTags

/**
 * Puts the app at the login screen before a journey, whatever the previous test left behind.
 *
 * Clearing the app's data is not an option: this instrumentation shares the `com.stylo.shop`
 * process, so `pm clear` kills the runner itself. Logging out through the UI is slower but keeps
 * one process and exercises a path a customer actually takes.
 */
internal fun ComposeTestRule.startLoggedOut() {
    if (!tagAppears(UiTags.CatalogProfile, timeoutMs = 8_000)) {
        waitForTag(UiTags.AuthEmail)
        return
    }
    onNodeWithTag(UiTags.CatalogProfile).performClick()
    waitForTag(UiTags.ProfileLogout)
    onNodeWithTag(UiTags.ProfileLogout).performScrollTo().performClick()
    waitForTag(UiTags.ProfileLogoutConfirm)
    onNodeWithTag(UiTags.ProfileLogoutConfirm).performClick()
    waitForTag(UiTags.AuthEmail)
}

internal fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = 15_000) {
    waitUntil(timeoutMs) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}

/** Non-throwing [waitForTag] probe: `false` when the tag never shows up within [timeoutMs]. */
internal fun ComposeTestRule.tagAppears(tag: String, timeoutMs: Long = 15_000): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return true
        Thread.sleep(100)
    }
    return onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
}

internal fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = 15_000) {
    waitUntil(timeoutMs) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

internal fun ComposeTestRule.waitForCard(name: String, timeoutMs: Long = 15_000) {
    waitUntil(timeoutMs) {
        onAllNodes(hasTestTag(UiTags.ProductCard) and hasText(name))
            .fetchSemanticsNodes().isNotEmpty()
    }
}

/** Polls [block] until it returns non-null; fails the test when the deadline passes. */
internal fun <T : Any> waitForValue(timeoutMs: Long = 15_000, block: () -> T?): T {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        block()?.let { return it }
        Thread.sleep(100)
    }
    return block() ?: error("condition not met within ${timeoutMs}ms")
}

/** Registers a fresh customer from the login screen and waits for the home header. */
internal fun ComposeTestRule.registerCustomer(
    auth: E2eAuthScreen,
    fullName: String,
    phone: String,
    email: String,
    password: String,
) {
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
