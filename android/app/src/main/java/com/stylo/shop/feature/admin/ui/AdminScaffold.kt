package com.stylo.shop.feature.admin.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import com.stylo.shop.core.ui.components.StyloConfirmDialog
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

enum class AdminTab {
    DASHBOARD,
    ORDERS,
    PRODUCTS,
    INVENTORY,
}

/**
 * Shared admin chrome: four operational tabs on the bottom bar, the remaining management
 * destinations (taxonomy, users, reports, logout) in a navigation drawer — Material 3 puts more
 * than five destinations in a drawer rather than hiding them behind an overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScaffold(
    selectedTab: AdminTab,
    title: String,
    onSelectTab: (AdminTab) -> Unit,
    onOpenTaxonomy: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenReports: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = stringResource(R.string.admin_drawer_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                DrawerEntry(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = stringResource(R.string.admin_menu_taxonomy),
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenTaxonomy()
                    },
                )
                DrawerEntry(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.admin_menu_users),
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenUsers()
                    },
                )
                DrawerEntry(
                    icon = Icons.Filled.DateRange,
                    label = stringResource(R.string.admin_menu_reports),
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenReports()
                    },
                )
                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DrawerEntry(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    label = stringResource(R.string.action_logout),
                    onClick = {
                        scope.launch { drawerState.close() }
                        confirmLogout = true
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.admin_menu_open),
                            )
                        }
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
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
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
                        icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                        label = { Text(stringResource(R.string.admin_tab_products)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == AdminTab.INVENTORY,
                        onClick = { onSelectTab(AdminTab.INVENTORY) },
                        // material-icons-core has no warehouse glyph: Build (operations) keeps the
                        // inventory tab visually distinct from the orders list.
                        icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                        label = { Text(stringResource(R.string.admin_tab_inventory)) },
                        modifier = Modifier.testTag(UiTags.AdminInventoryTab),
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            content = content,
        )
    }

    if (confirmLogout) {
        StyloConfirmDialog(
            title = stringResource(R.string.profile_logout_title),
            body = stringResource(R.string.profile_logout_body),
            confirmLabel = stringResource(R.string.action_logout),
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

/** One drawer destination: icon plus permanent label, 56dp tall. */
@Composable
private fun DrawerEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(icon, contentDescription = null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
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

