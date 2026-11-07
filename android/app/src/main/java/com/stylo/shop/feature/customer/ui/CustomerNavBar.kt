package com.stylo.shop.feature.customer.ui

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.stylo.shop.core.ui.UiTags

/** Material3 bottom bar shared by the five customer tabs; pushed screens never render it. */
@Composable
fun CustomerNavBar(
    selectedTab: CustomerTab,
    cartCount: Int,
    onSelectTab: (CustomerTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CustomerTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                modifier = Modifier.testTag(tab.tag),
                icon = {
                    if (tab == CustomerTab.CART) {
                        BadgedBox(
                            badge = {
                                if (cartCount > 0) {
                                    Badge(modifier = Modifier.testTag(UiTags.NavCartBadge)) {
                                        Text(cartCount.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(tab.icon, contentDescription = null)
                        }
                    } else {
                        Icon(tab.icon, contentDescription = null)
                    }
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}
