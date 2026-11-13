package com.stylo.shop.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.stylo.shop.R
import com.stylo.shop.core.model.User
import com.stylo.shop.core.model.UserRole
import com.stylo.shop.core.storage.SessionState
import com.stylo.shop.feature.admin.ui.dashboard.AdminDashboardScreen
import com.stylo.shop.feature.admin.ui.AdminTab
import com.stylo.shop.feature.admin.ui.products.AdminProductFormScreen
import com.stylo.shop.feature.admin.ui.products.AdminProductListScreen
import com.stylo.shop.feature.admin.ui.inventory.AdminReceiptCreateScreen
import com.stylo.shop.feature.admin.ui.inventory.AdminReceiptDetailScreen
import com.stylo.shop.feature.admin.ui.inventory.AdminReceiptListScreen
import com.stylo.shop.feature.admin.ui.orders.AdminOrderDetailScreen
import com.stylo.shop.feature.admin.ui.orders.AdminOrderListScreen
import com.stylo.shop.feature.admin.ui.taxonomy.AdminTaxonomyScreen
import com.stylo.shop.feature.admin.ui.reports.AdminReportsScreen
import com.stylo.shop.feature.admin.ui.users.AdminUserListScreen
import com.stylo.shop.feature.auth.ui.AuthMode
import com.stylo.shop.feature.auth.ui.AuthScreen
import com.stylo.shop.feature.auth.ui.ForgotPasswordScreen
import com.stylo.shop.feature.cart.ui.CartScreen
import com.stylo.shop.feature.catalog.ui.CatalogScreen
import com.stylo.shop.feature.checkout.ui.CheckoutScreen
import com.stylo.shop.feature.customer.ui.CustomerShellViewModel
import com.stylo.shop.feature.customer.ui.CustomerTab
import com.stylo.shop.feature.customer.ui.CustomerTabScaffold
import com.stylo.shop.feature.home.ui.HomeScreen
import com.stylo.shop.feature.orders.ui.OrderDetailScreen
import com.stylo.shop.feature.orders.ui.OrderListScreen
import com.stylo.shop.feature.profile.ui.ProfileScreen
import com.stylo.shop.feature.profile.ui.address.AddressFormScreen
import com.stylo.shop.feature.profile.ui.address.AddressListScreen

import com.stylo.shop.feature.catalog.ui.ProductDetailScreen
@Composable
fun StyloNavHost(
    sessionState: SessionState,
    onRetrySession: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (sessionState) {
        SessionState.Loading -> LoadingScreen(modifier)
        SessionState.Unavailable -> UnavailableScreen(onRetrySession, modifier)
        // Both branches share one call site: a guest logging in switches branch data but must
        // keep the shell's nav back stack and pending tab instead of rebuilding the graph.
        SessionState.Unauthenticated, is SessionState.Authenticated -> SessionNavHost(
            user = (sessionState as? SessionState.Authenticated)?.user,
            onLogout = onLogout,
            modifier = modifier,
        )
    }
}

@Composable
private fun SessionNavHost(
    user: User?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startDestination: Any = when (user?.role) {
        UserRole.ADMIN -> AdminGraph
        // Guests browse Home/Catalog; the guarded tabs send them to Login first.
        UserRole.CUSTOMER, null -> CustomerGraph
    }

    // A new start graph means a fresh back stack: auth and role graphs never share history.
    key(startDestination) {
        val navController = rememberNavController()
        val shellViewModel: CustomerShellViewModel = hiltViewModel()
        val cartCount by shellViewModel.cartCount.collectAsStateWithLifecycle()
        var pendingTab by rememberSaveable { mutableStateOf<CustomerTab?>(null) }
        // Destination content reads the session through a State, so a guest logging in updates
        // the shell without rebuilding the navigation graph.
        val currentUser by rememberUpdatedState(user)

        fun openCustomerTab(tab: CustomerTab) {
            if (tab.requiresAuth && currentUser == null) {
                pendingTab = tab
                navController.navigate(LoginRoute)
            } else {
                navController.navigateToCustomerTab(tab)
            }
        }

        LaunchedEffect(user) {
            val pending = pendingTab
            pendingTab = null
            if (user != null) {
                if (pending != null && user.role == UserRole.CUSTOMER) {
                    navController.navigatePlain(pending)
                } else if (pending == null) {
                    // In-place login from a guest tab: drop the auth screen still on top.
                    val destination = navController.currentBackStackEntry?.destination
                    if (destination?.hasRoute<LoginRoute>() == true ||
                        destination?.hasRoute<RegisterRoute>() == true
                    ) {
                        navController.popBackStack()
                    }
                }
            } else if (navController.currentBackStackEntry?.destination.isGuardedTab()) {
                navController.navigatePlain(CustomerTab.HOME)
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
        ) {
            navigation<AuthGraph>(startDestination = LoginRoute) {
                composable<LoginRoute> {
                    AuthScreen(
                        mode = AuthMode.LOGIN,
                        onSwitchMode = { navController.switchAuthMode(RegisterRoute) },
                        onForgotPassword = { navController.navigate(ForgotPasswordRoute) },
                    )
                }
                composable<RegisterRoute> {
                    AuthScreen(
                        mode = AuthMode.REGISTER,
                        onSwitchMode = { navController.switchAuthMode(LoginRoute) },
                        onForgotPassword = { navController.navigate(ForgotPasswordRoute) },
                    )
                }
                composable<ForgotPasswordRoute> {
                    ForgotPasswordScreen(
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack<LoginRoute>(inclusive = false) },
                    )
                }
            }
            navigation<CustomerGraph>(startDestination = HomeRoute) {
                composable<HomeRoute> {
                    CustomerTabScaffold(
                        selectedTab = CustomerTab.HOME,
                        cartCount = cartCount,
                        onSelectTab = ::openCustomerTab,
                    ) {
                        HomeScreen(
                            user = currentUser,
                            onOpenProduct = { productId ->
                                navController.navigate(ProductDetailRoute(productId))
                            },
                            onOpenCatalog = { categoryId, focusSearch ->
                                navController.navigate(CatalogRoute(categoryId, focusSearch)) {
                                    popUpTo<HomeRoute> { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }
                composable<CatalogRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<CatalogRoute>()
                    CustomerTabScaffold(
                        selectedTab = CustomerTab.CATALOG,
                        cartCount = cartCount,
                        onSelectTab = ::openCustomerTab,
                    ) {
                        CatalogScreen(
                            onNavigateToProduct = { productId ->
                                navController.navigate(ProductDetailRoute(productId))
                            },
                            categoryId = route.categoryId,
                            focusSearch = route.focusSearch,
                        )
                    }
                }
                composable<CartRoute> {
                    CustomerTabScaffold(
                        selectedTab = CustomerTab.CART,
                        cartCount = cartCount,
                        onSelectTab = ::openCustomerTab,
                    ) {
                        CartScreen(
                            onShop = { openCustomerTab(CustomerTab.HOME) },
                            onCheckout = { navController.navigate(CheckoutRoute) },
                        )
                    }
                }
                composable<CheckoutRoute> {
                    CheckoutScreen(
                        onBack = { navController.popBackStack() },
                        onAddAddress = { navController.navigate(AddressCreateRoute) },
                        onBackToCart = { navController.popBackStack() },
                        onOrderPlaced = { orderId ->
                            // The cart is consumed by the order, so it must not stay behind it.
                            navController.navigate(OrderDetailRoute(orderId)) {
                                popUpTo<CartRoute> { inclusive = true }
                            }
                        },
                    )
                }
                composable<OrderDetailRoute> {
                    OrderDetailScreen(onBack = { navController.popBackStack() })
                }
                composable<OrdersRoute> {
                    CustomerTabScaffold(
                        selectedTab = CustomerTab.ORDERS,
                        cartCount = cartCount,
                        onSelectTab = ::openCustomerTab,
                    ) {
                        OrderListScreen(
                            onOpenOrder = { orderId ->
                                navController.navigate(OrderDetailRoute(orderId))
                            },
                        )
                    }
                }
                composable<ProfileRoute> {
                    CustomerTabScaffold(
                        selectedTab = CustomerTab.PROFILE,
                        cartCount = cartCount,
                        onSelectTab = ::openCustomerTab,
                    ) {
                        ProfileScreen(
                            onOpenAddresses = { navController.navigate(AddressListRoute) },
                            onLogout = {
                                navController.navigateToCustomerTab(CustomerTab.HOME)
                                onLogout()
                            },
                        )
                    }
                }
                composable<AddressListRoute> {
                    AddressListScreen(
                        onBack = { navController.popBackStack() },
                        onAdd = { navController.navigate(AddressCreateRoute) },
                        onEdit = { addressId ->
                            navController.navigate(AddressEditRoute(addressId))
                        },
                    )
                }
                composable<AddressCreateRoute> {
                    AddressFormScreen(onBack = { navController.popBackStack() })
                }
                composable<AddressEditRoute> {
                    AddressFormScreen(onBack = { navController.popBackStack() })
                }
                composable<ProductDetailRoute> {
                    ProductDetailScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCart = { openCustomerTab(CustomerTab.CART) },
                        isSignedIn = currentUser != null,
                        onRequireLogin = { openCustomerTab(CustomerTab.CART) },
                    )
                }
            }
            // The admin graph only exists for an admin session: no destination can render a
            // blank page while the session is being torn down.
            if (currentUser?.role == UserRole.ADMIN) {
                navigation<AdminGraph>(startDestination = AdminDashboardRoute) {
                    composable<AdminDashboardRoute> {
                        AdminDashboardScreen(
                            onSelectTab = { tab -> navController.navigateAdminTab(tab) },
                            onOpenTaxonomy = { navController.navigate(AdminTaxonomyRoute) },
                            onOpenUsers = { navController.navigate(AdminUsersRoute) },
                            onOpenReports = { navController.navigate(AdminReportsRoute) },
                            onOpenOrder = { orderId ->
                                navController.navigate(AdminOrderDetailRoute(orderId))
                            },
                            onLogout = onLogout,
                        )
                    }
                    composable<AdminProductsRoute> {
                        AdminProductListScreen(
                            onAdd = { navController.navigate(AdminProductCreateRoute) },
                            onEdit = { productId ->
                                navController.navigate(AdminProductEditRoute(productId))
                            },
                            onSelectTab = { tab -> navController.navigateAdminTab(tab) },
                            onOpenTaxonomy = { navController.navigate(AdminTaxonomyRoute) },
                            onOpenUsers = { navController.navigate(AdminUsersRoute) },
                            onOpenReports = { navController.navigate(AdminReportsRoute) },
                            onLogout = onLogout,
                        )
                    }
                    composable<AdminProductCreateRoute> {
                        AdminProductFormScreen(
                            productId = null,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<AdminProductEditRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<AdminProductEditRoute>()
                        AdminProductFormScreen(
                            productId = route.productId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<AdminOrdersRoute> {
                        AdminOrderListScreen(
                            onOpenOrder = { orderId ->
                                navController.navigate(AdminOrderDetailRoute(orderId))
                            },
                            onSelectTab = { tab -> navController.navigateAdminTab(tab) },
                            onOpenTaxonomy = { navController.navigate(AdminTaxonomyRoute) },
                            onOpenUsers = { navController.navigate(AdminUsersRoute) },
                            onOpenReports = { navController.navigate(AdminReportsRoute) },
                            onLogout = onLogout,
                        )
                    }
                    composable<AdminOrderDetailRoute> {
                        AdminOrderDetailScreen(onBack = { navController.popBackStack() })
                    }
                    composable<AdminInventoryRoute> {
                        AdminReceiptListScreen(
                            onSelectTab = { tab -> navController.navigateAdminTab(tab) },
                            onOpenTaxonomy = { navController.navigate(AdminTaxonomyRoute) },
                            onOpenUsers = { navController.navigate(AdminUsersRoute) },
                            onOpenReports = { navController.navigate(AdminReportsRoute) },
                            onLogout = onLogout,
                            onAdd = { navController.navigate(AdminReceiptCreateRoute) },
                            onOpenReceipt = { receiptId ->
                                navController.navigate(AdminReceiptDetailRoute(receiptId))
                            },
                        )
                    }
                    composable<AdminReceiptCreateRoute> {
                        AdminReceiptCreateScreen(
                            onBack = { navController.popBackStack() },
                            onCreated = { receiptId ->
                                navController.navigate(AdminReceiptDetailRoute(receiptId)) {
                                    popUpTo<AdminReceiptCreateRoute> { inclusive = true }
                                }
                            },
                        )
                    }
                    composable<AdminReceiptDetailRoute> {
                        AdminReceiptDetailScreen(onBack = { navController.popBackStack() })
                    }
                    composable<AdminUsersRoute> {
                        AdminUserListScreen(onBack = { navController.popBackStack() })
                    }
                    composable<AdminReportsRoute> {
                        AdminReportsScreen(onBack = { navController.popBackStack() })
                    }
                    composable<AdminTaxonomyRoute> {
                        AdminTaxonomyScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

private fun customerTabDestination(tab: CustomerTab): Any = when (tab) {
    CustomerTab.HOME -> HomeRoute
    CustomerTab.CATALOG -> CatalogRoute()
    CustomerTab.CART -> CartRoute
    CustomerTab.ORDERS -> OrdersRoute
    CustomerTab.PROFILE -> ProfileRoute
}

private fun NavHostController.navigateToCustomerTab(tab: CustomerTab) {
    // Home is the popUpTo anchor, so the save/restore pattern below would restore the stack it
    // just popped and leave the previous tab on screen.
    if (tab == CustomerTab.HOME) {
        navigatePlain(CustomerTab.HOME)
        return
    }
    navigate(customerTabDestination(tab)) {
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Pops back to Home and opens [tab] without `saveState`/`restoreState`. Saving the popped Login
 * entry lets a later navigate restore it, and going to the popUpTo anchor would re-restore the
 * stack it just popped (Home showing the tab it came from).
 */
private fun NavHostController.navigatePlain(tab: CustomerTab) {
    navigate(customerTabDestination(tab)) {
        popUpTo<HomeRoute> { inclusive = false }
        launchSingleTop = true
    }
}

private fun NavDestination?.isGuardedTab(): Boolean =
    this?.let { it.hasRoute<CartRoute>() || it.hasRoute<OrdersRoute>() || it.hasRoute<ProfileRoute>() } == true

private fun NavHostController.navigateAdminTab(tab: AdminTab) {
    val destination: Any = when (tab) {
        AdminTab.DASHBOARD -> AdminDashboardRoute
        AdminTab.ORDERS -> AdminOrdersRoute
        AdminTab.PRODUCTS -> AdminProductsRoute
        AdminTab.INVENTORY -> AdminInventoryRoute
    }
    navigate(destination) {
        // Same save/restore contract as the customer tabs: filters and scroll survive a tab switch.
        popUpTo<AdminDashboardRoute> {
            inclusive = false
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.switchAuthMode(destination: Any) {
    navigate(destination) {
        popUpTo<AuthGraph> { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun UnavailableScreen(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.headlineLarge.copy(
                letterSpacing = 6.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.session_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
        Spacer(Modifier.weight(1f))
    }
}
