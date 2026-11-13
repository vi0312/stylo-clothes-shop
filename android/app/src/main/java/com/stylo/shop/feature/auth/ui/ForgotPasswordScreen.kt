package com.stylo.shop.feature.auth.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.core.ui.components.StyloBusyButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.auth_forgot_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(UiTags.AuthResetBack),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.step) {
                ForgotPasswordStep.EMAIL -> EmailStep(
                    state = state,
                    onEmailChange = viewModel::onEmailChange,
                    onSubmit = viewModel::requestCode,
                )
                ForgotPasswordStep.CODE -> CodeStep(
                    state = state,
                    onCodeChange = viewModel::onCodeChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onChangeEmail = viewModel::onChangeEmail,
                    onResend = viewModel::requestCode,
                    onSubmit = viewModel::submit,
                )
                ForgotPasswordStep.DONE -> DoneStep(onDone = onDone)
            }
        }
    }
}

@Composable
private fun EmailStep(
    state: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Text(
        text = stringResource(R.string.auth_forgot_email_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    AuthTextField(
        value = state.email,
        onValueChange = onEmailChange,
        label = stringResource(R.string.auth_email),
        error = state.errors.email,
        testTag = UiTags.AuthResetEmail,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                onSubmit()
            },
        ),
    )
    if (state.formError != null) {
        Spacer(Modifier.height(16.dp))
        ErrorBanner(message = stringResource(state.formError))
    }
    Spacer(Modifier.height(24.dp))
    StyloBusyButton(
        text = stringResource(R.string.auth_forgot_send_code),
        busy = state.submitting,
        onClick = {
            focusManager.clearFocus()
            onSubmit()
        },
        testTag = UiTags.AuthResetSendCode,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    )
}

@Composable
private fun CodeStep(
    state: ForgotPasswordUiState,
    onCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onChangeEmail: () -> Unit,
    onResend: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.auth_forgot_code_hint, state.email),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
        onClick = onChangeEmail,
        modifier = Modifier.testTag(UiTags.AuthResetChangeEmail),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.auth_forgot_change_email),
            style = MaterialTheme.typography.labelMedium,
        )
    }
    Spacer(Modifier.height(8.dp))
    AuthTextField(
        value = state.code,
        onValueChange = onCodeChange,
        label = stringResource(R.string.auth_forgot_code),
        error = state.errors.code,
        testTag = UiTags.AuthResetCode,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
    )
    Spacer(Modifier.height(12.dp))
    AuthTextField(
        value = state.password,
        onValueChange = onPasswordChange,
        label = stringResource(R.string.auth_forgot_new_password),
        error = state.errors.password,
        testTag = UiTags.AuthResetPassword,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                onSubmit()
            },
        ),
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingToggle = {
            TextButton(
                onClick = { passwordVisible = !passwordVisible },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(
                        if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
    )
    if (state.formError != null) {
        Spacer(Modifier.height(16.dp))
        ErrorBanner(message = stringResource(state.formError))
    }
    Spacer(Modifier.height(24.dp))
    StyloBusyButton(
        text = stringResource(R.string.auth_forgot_submit),
        busy = state.submitting,
        onClick = {
            focusManager.clearFocus()
            onSubmit()
        },
        testTag = UiTags.AuthResetSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    )
    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = onResend,
        enabled = state.resendSeconds == 0 && !state.submitting,
        modifier = Modifier.testTag(UiTags.AuthResetResend),
    ) {
        Text(
            text = if (state.resendSeconds > 0) {
                stringResource(R.string.auth_forgot_resend_wait, state.resendSeconds)
            } else {
                stringResource(R.string.auth_forgot_resend)
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun DoneStep(onDone: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.auth_forgot_done_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.auth_forgot_done_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    StyloBusyButton(
        text = stringResource(R.string.auth_forgot_done_cta),
        busy = false,
        onClick = onDone,
        testTag = UiTags.AuthResetDone,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    )
}
