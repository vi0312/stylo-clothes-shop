package com.stylo.shop.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stylo.shop.R
import com.stylo.shop.core.model.User
import com.stylo.shop.core.ui.SignedInScreen

@Composable
fun HomeScreen(user: User, onLogout: () -> Unit) {
    SignedInScreen(
        title = stringResource(R.string.home_title),
        user = user,
        onLogout = onLogout,
    )
}
