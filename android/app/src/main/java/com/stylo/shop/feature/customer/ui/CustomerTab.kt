package com.stylo.shop.feature.customer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.stylo.shop.R
import com.stylo.shop.core.ui.UiTags

/** The five customer tabs, in bottom-bar order (docs/ui.md §Điều hướng). */
enum class CustomerTab(
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String,
) {
    HOME(R.string.home_title, Icons.Filled.Home, UiTags.NavHome),
    CATALOG(R.string.nav_catalog, Icons.Filled.Search, UiTags.NavCatalog),
    CART(R.string.nav_cart, Icons.Filled.ShoppingCart, UiTags.NavCart),
    ORDERS(R.string.nav_orders, Icons.AutoMirrored.Filled.List, UiTags.NavOrders),
    PROFILE(R.string.nav_profile, Icons.Filled.Person, UiTags.NavProfile),
    ;

    /** Guests browse Home/Catalog; the other tabs ask for a session first. */
    val requiresAuth: Boolean
        get() = this == CART || this == ORDERS || this == PROFILE
}
