package com.stylo.shop.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.UiEffectHost
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.feature.profile.model.PROFILE_NAME_MAX
import com.stylo.shop.feature.profile.model.PROFILE_PHONE_MAX

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenAddresses: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }

    UiEffectHost(viewModel.effects, snackbarHostState)

    ProfileDetailScaffold(
        title = stringResource(R.string.profile_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.email,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(stringResource(R.string.profile_email)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTags.ProfileEmail),
            )

            OutlinedTextField(
                value = state.fullName,
                onValueChange = viewModel::onFullNameChange,
                singleLine = true,
                label = { Text(stringResource(R.string.profile_full_name)) },
                isError = state.fullNameError != null,
                supportingText = state.fullNameError?.let { error ->
                    { Text(profileFieldErrorText(error, PROFILE_NAME_MAX)) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTags.ProfileFullName),
            )

            OutlinedTextField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                singleLine = true,
                label = { Text(stringResource(R.string.profile_phone)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = state.phoneError != null,
                supportingText = state.phoneError?.let { error ->
                    { Text(profileFieldErrorText(error, PROFILE_PHONE_MAX)) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTags.ProfilePhone),
            )

            state.formError?.let { ProfileErrorBanner(it) }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(UiTags.ProfileSave),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.profile_save))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            ProfileLinkRow(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.profile_orders),
                hint = stringResource(R.string.profile_orders_hint),
                onClick = onOpenOrders,
                tag = UiTags.ProfileOrders,
            )

            ProfileLinkRow(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.profile_addresses),
                hint = stringResource(R.string.profile_addresses_hint),
                onClick = onOpenAddresses,
                tag = UiTags.ProfileAddresses,
            )

            OutlinedButton(
                onClick = { confirmLogout = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(UiTags.ProfileLogout),
            ) {
                Text(stringResource(R.string.action_logout))
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.profile_logout_title)) },
            text = { Text(stringResource(R.string.profile_logout_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    },
                    modifier = Modifier.testTag(UiTags.ProfileLogoutConfirm),
                ) {
                    Text(stringResource(R.string.action_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileLinkRow(
    icon: ImageVector,
    title: String,
    hint: String,
    onClick: () -> Unit,
    tag: String,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
