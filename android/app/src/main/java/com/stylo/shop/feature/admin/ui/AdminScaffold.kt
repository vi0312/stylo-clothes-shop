package com.stylo.shop.feature.admin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.stylo.shop.R
import com.stylo.shop.core.ui.UiTags

enum class AdminTab {
    DASHBOARD,
    ORDERS,
    PRODUCTS,
    INVENTORY,
}

/**
 * Shared admin chrome. Only destinations that exist are listed; users and reports join the bar as
 * their tickets land, so no tab points at an empty route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScaffold(
    selectedTab: AdminTab,
    title: String,
    onSelectTab: (AdminTab) -> Unit,
    onOpenTaxonomy: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    OverflowMenu(onOpenTaxonomy = onOpenTaxonomy, onLogout = onLogout)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == AdminTab.DASHBOARD,
                    onClick = { onSelectTab(AdminTab.DASHBOARD) },
                    icon = {
                        Icon(Icons.Filled.Home, contentDescription = null)
                    },
                    label = { Text(stringResource(R.string.admin_tab_dashboard)) },
                )
                NavigationBarItem(
                    selected = selectedTab == AdminTab.ORDERS,
                    onClick = { onSelectTab(AdminTab.ORDERS) },
                    icon = {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                    },
                    label = { Text(stringResource(R.string.admin_tab_orders)) },
                    modifier = Modifier.testTag(UiTags.AdminOrdersTab),
                )
                NavigationBarItem(
                    selected = selectedTab == AdminTab.PRODUCTS,
                    onClick = { onSelectTab(AdminTab.PRODUCTS) },
                    icon = {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null)
                    },
                    label = { Text(stringResource(R.string.admin_tab_products)) },
                )
                NavigationBarItem(
                    selected = selectedTab == AdminTab.INVENTORY,
                    onClick = { onSelectTab(AdminTab.INVENTORY) },
                    icon = {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                    },
                    label = { Text(stringResource(R.string.admin_tab_inventory)) },
                    modifier = Modifier.testTag(UiTags.AdminInventoryTab),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = content,
    )
}

/** Chrome for pushed admin screens: a back affordance, no tab bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = content,
    )
}

@Composable
private fun OverflowMenu(
    onOpenTaxonomy: () -> Unit,
    onLogout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.action_more),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.admin_menu_taxonomy)) },
                onClick = {
                    expanded = false
                    onOpenTaxonomy()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_logout)) },
                onClick = {
                    expanded = false
                    onLogout()
                },
            )
        }
    }
}
