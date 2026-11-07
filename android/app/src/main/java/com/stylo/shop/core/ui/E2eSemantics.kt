package com.stylo.shop.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Compose dialogs, popups and modal sheets render into their own window, so the flag MainActivity
 * sets on the app root never reaches them. A popup root that carries [UiTags] takes this modifier
 * so its tags stay visible to uiautomator and mobilecli as resource-ids.
 */
fun Modifier.e2eTagsAsResourceId(): Modifier = semantics { testTagsAsResourceId = true }
