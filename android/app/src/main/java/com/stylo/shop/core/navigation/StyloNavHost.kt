package com.stylo.shop.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stylo.shop.feature.health.ui.HealthScreen

@Composable
fun StyloNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HealthRoute,
    ) {
        composable<HealthRoute> {
            HealthScreen()
        }
    }
}
