package com.stylo.shop.e2e

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
