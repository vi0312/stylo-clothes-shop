package com.stylo.shop.feature.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Chrome for the five tab destinations: screen content above the shared bar, bottom window inset
 * owned by the bar itself so screens do not leave a second gap above it.
 */
@Composable
fun CustomerTabScaffold(
    selectedTab: CustomerTab,
    cartCount: Int,
    onSelectTab: (CustomerTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            content()
        }
        CustomerNavBar(
            selectedTab = selectedTab,
            cartCount = cartCount,
            onSelectTab = onSelectTab,
        )
    }
}
