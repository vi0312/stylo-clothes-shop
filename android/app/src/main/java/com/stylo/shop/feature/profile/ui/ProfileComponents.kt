package com.stylo.shop.feature.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stylo.shop.R
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.feature.profile.model.ProfileFieldError

/** Pushed customer chrome: back affordance, no bottom bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag(UiTags.ProfileBack),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = { actions() },
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
fun profileFieldErrorText(error: ProfileFieldError, maxLength: Int): String = when (error) {
    ProfileFieldError.REQUIRED -> stringResource(R.string.profile_field_required)
    ProfileFieldError.TOO_LONG -> stringResource(R.string.profile_field_too_long, maxLength)
}

@Composable
fun ProfileErrorBanner(error: ApiError) {
    Text(
        text = stringResource(ErrorMessages.resourceId(error)),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

@Composable
fun ProfileErrorContent(
    error: ApiError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(ErrorMessages.resourceId(error)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(UiTags.ProfileRetry),
        ) {
            Text(stringResource(R.string.action_retry))
        }
    }
}
