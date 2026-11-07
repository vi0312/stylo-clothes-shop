package com.stylo.shop.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stylo.shop.core.model.User
import com.stylo.shop.feature.catalog.ui.CatalogScreen

@Composable
fun HomeScreen(
    user: User,
    onNavigateToProduct: (Long) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CatalogScreen(
        onNavigateToProduct = onNavigateToProduct,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToCart = onNavigateToCart,
        user = user,
        modifier = modifier,
    )
}
