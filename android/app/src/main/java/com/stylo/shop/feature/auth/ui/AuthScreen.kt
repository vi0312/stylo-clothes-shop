package com.stylo.shop.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.StyloLockupStacked
import com.stylo.shop.core.ui.UiTags

@Composable
fun AuthScreen(
    mode: AuthMode,
    onSwitchMode: (AuthMode) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    AuthContent(
        mode = mode,
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onFullNameChange = viewModel::onFullNameChange,
        onPhoneChange = viewModel::onPhoneChange,
        onSwitchMode = onSwitchMode,
        onSubmit = {
            focusManager.clearFocus()
            viewModel.submit(mode)
        },
    )
}

@Composable
private fun AuthContent(
    mode: AuthMode,
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFullNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSwitchMode: (AuthMode) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val isRegister = mode == AuthMode.REGISTER

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        StyloLockupStacked(
            modifier = Modifier.height(96.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(if (isRegister) R.string.auth_register_subtitle else R.string.auth_login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AuthMode.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = item == mode,
                    onClick = { if (item != mode) onSwitchMode(item) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = AuthMode.entries.size),
                    label = { Text(stringResource(item.labelRes)) },
                    modifier = Modifier.testTag(
                        if (item == AuthMode.REGISTER) UiTags.AuthRegisterTab else UiTags.AuthLoginTab,
                    ),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        if (isRegister) {
            AuthTextField(
                value = state.fullName,
                onValueChange = onFullNameChange,
                label = stringResource(R.string.auth_full_name),
                error = state.errors.fullName,
                testTag = UiTags.AuthFullName,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                label = stringResource(R.string.auth_phone_optional),
                error = state.errors.phone,
                testTag = UiTags.AuthPhone,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
            Spacer(Modifier.height(12.dp))
        }

        AuthTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.auth_email),
            error = state.errors.email,
            testTag = UiTags.AuthEmail,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
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
            label = stringResource(R.string.auth_password),
            error = state.errors.password,
            testTag = UiTags.AuthPassword,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (isRegister) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { onSubmit() },
            ),
            visualTransformation = PasswordVisualTransformation(),
        )

        if (state.formError != null) {
            Spacer(Modifier.height(16.dp))
            ErrorBanner(message = stringResource(state.formError))
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSubmit,
            enabled = !state.submitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(UiTags.AuthSubmit),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = stringResource(if (isRegister) R.string.auth_register_cta else R.string.auth_login_cta),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: AuthFieldError?,
    testTag: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error.message()) }
        } else {
            null
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun AuthFieldError.message(): String = when (this) {
    AuthFieldError.Required -> stringResource(R.string.auth_field_required)
    AuthFieldError.InvalidEmail -> stringResource(R.string.auth_field_invalid_email)
    AuthFieldError.PasswordTooShort -> stringResource(R.string.auth_field_password_short)
    AuthFieldError.EmailTaken -> stringResource(R.string.error_email_taken)
    AuthFieldError.Invalid -> stringResource(R.string.auth_field_invalid)
    is AuthFieldError.TooLong -> stringResource(R.string.auth_field_too_long, max)
}

private val AuthMode.labelRes: Int
    get() = when (this) {
        AuthMode.LOGIN -> R.string.auth_login_tab
        AuthMode.REGISTER -> R.string.auth_register_tab
    }
