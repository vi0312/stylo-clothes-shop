package com.stylo.shop.feature.profile.ui.address

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.feature.profile.model.ADDRESS_LINE_MAX
import com.stylo.shop.feature.profile.model.PROFILE_NAME_MAX
import com.stylo.shop.feature.profile.model.PROFILE_PHONE_MAX
import com.stylo.shop.feature.profile.ui.ProfileDetailScaffold
import com.stylo.shop.feature.profile.ui.ProfileErrorBanner
import com.stylo.shop.feature.profile.ui.ProfileErrorContent
import com.stylo.shop.feature.profile.ui.profileFieldErrorText

@Composable
fun AddressFormScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddressFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    ProfileDetailScaffold(
        title = stringResource(
            if (state.addressId == null) R.string.address_form_add_title
            else R.string.address_form_edit_title,
        ),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.loadError != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                ProfileErrorContent(
                    error = requireNotNull(state.loadError),
                    onRetry = { viewModel.load() },
                )
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = state.recipientName,
                    onValueChange = viewModel::onRecipientNameChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.address_form_recipient)) },
                    isError = state.recipientNameError != null,
                    supportingText = state.recipientNameError?.let { error ->
                        { Text(profileFieldErrorText(error, PROFILE_NAME_MAX)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.phone,
                    onValueChange = viewModel::onPhoneChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.address_form_phone)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = state.phoneError != null,
                    supportingText = state.phoneError?.let { error ->
                        { Text(profileFieldErrorText(error, PROFILE_PHONE_MAX)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.line,
                    onValueChange = viewModel::onLineChange,
                    minLines = 2,
                    maxLines = 3,
                    label = { Text(stringResource(R.string.address_form_line)) },
                    isError = state.lineError != null,
                    supportingText = state.lineError?.let { error ->
                        { Text(profileFieldErrorText(error, ADDRESS_LINE_MAX)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.address_form_default),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(
                                when {
                                    state.addressId == null -> R.string.address_form_first_hint
                                    state.isDefaultLocked -> R.string.address_form_default_locked
                                    else -> R.string.address_form_default_hint
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.isDefault,
                        onCheckedChange = viewModel::onDefaultChange,
                        enabled = !state.isDefaultLocked,
                    )
                }

                state.formError?.let { ProfileErrorBanner(it) }

                Button(
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.address_form_save))
                    }
                }
            }
        }
    }
}
