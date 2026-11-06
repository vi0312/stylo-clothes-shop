package com.stylo.shop.feature.admin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stylo.shop.R
import com.stylo.shop.core.model.User
import com.stylo.shop.core.ui.SignedInScreen

@Composable
fun AdminDashboardScreen(user: User, onLogout: () -> Unit) {
    SignedInScreen(
        title = stringResource(R.string.admin_dashboard_title),
        user = user,
        onLogout = onLogout,
    )
}
