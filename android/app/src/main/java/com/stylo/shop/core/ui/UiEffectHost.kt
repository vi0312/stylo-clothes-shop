package com.stylo.shop.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

@Composable
fun UiEffectHost(
    effects: Flow<UiEffect>,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    LaunchedEffect(effects) {
        effects.collect { effect ->
            val message = when (effect) {
                is UiEffect.ShowMessage -> effect.message
                is UiEffect.ShowMessageRes -> context.getString(
                    effect.messageRes,
                    *effect.args.toTypedArray(),
                )
                is UiEffect.ShowError -> context.getString(
                    ErrorMessages.resourceId(effect.error),
                )
            }
            snackbarHostState.showSnackbar(message)
        }
    }
}
