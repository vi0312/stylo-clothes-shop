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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.stylo.shop.feature.admin.ui.AdminDashboardScreen
import com.stylo.shop.feature.admin.ui.AdminTab
import com.stylo.shop.feature.admin.ui.products.AdminProductFormScreen
import com.stylo.shop.feature.admin.ui.products.AdminProductListScreen
import com.stylo.shop.feature.admin.ui.taxonomy.AdminTaxonomyScreen
import com.stylo.shop.feature.auth.ui.AuthMode
import com.stylo.shop.feature.auth.ui.AuthScreen
import com.stylo.shop.feature.home.ui.HomeScreen
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
        SessionState.Unauthenticated -> SessionNavHost(user = null, onLogout = onLogout, modifier = modifier)
        is SessionState.Authenticated ->
            SessionNavHost(user = sessionState.user, onLogout = onLogout, modifier = modifier)
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
        UserRole.CUSTOMER -> CustomerGraph
        null -> AuthGraph
    }

    // A new start graph means a fresh back stack: auth and role graphs never share history.
    key(startDestination) {
        val navController = rememberNavController()
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
                    )
                }
                composable<RegisterRoute> {
                    AuthScreen(
                        mode = AuthMode.REGISTER,
                        onSwitchMode = { navController.switchAuthMode(LoginRoute) },
                    )
                }
            }
            navigation<CustomerGraph>(startDestination = HomeRoute) {
                composable<HomeRoute> {
                    if (user != null) {
                        HomeScreen(
                            user = user,
                            onNavigateToProduct = { productId ->
                                navController.navigate(ProductDetailRoute(productId))
                            },
                            onNavigateToProfile = { navController.navigate(ProfileRoute) },
                        )
                    }
                }
                composable<ProfileRoute> {
                    ProfileScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAddresses = { navController.navigate(AddressListRoute) },
                        onLogout = onLogout,
                    )
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
                    )
                }
            }
            navigation<AdminGraph>(startDestination = AdminDashboardRoute) {
                composable<AdminDashboardRoute> {
                    if (user != null) {
                        AdminDashboardScreen(
                            user = user,
                            onSelectTab = { tab ->
                                if (tab == AdminTab.PRODUCTS) {
                                    navController.navigateAdminTab(AdminProductsRoute)
                                }
                            },
                            onOpenTaxonomy = { navController.navigate(AdminTaxonomyRoute) },
                            onLogout = onLogout,
                        )
                    }
                }
                composable<AdminProductsRoute> {
                    AdminProductListScreen(
                        onAdd = { navController.navigate(AdminProductCreateRoute) },
                        onEdit = { productId ->
                            navController.navigate(AdminProductEditRoute(productId))
                        },
                        onSelectTab = { tab ->
                            if (tab == AdminTab.DASHBOARD) {
                                navController.navigateAdminTab(AdminDashboardRoute)
                            }
                        },
                        onOpenTaxonomy = { navController.navigate(AdminTaxonomyRoute) },
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
                composable<AdminTaxonomyRoute> {
                    AdminTaxonomyScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

private fun NavHostController.navigateAdminTab(destination: Any) {
    navigate(destination) {
        popUpTo<AdminDashboardRoute> { inclusive = false }
        launchSingleTop = true
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
