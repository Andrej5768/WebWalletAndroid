package org.andrewdev.webwallet.feature.auth.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.auth.PasswordProblem
import org.andrewdev.webwallet.feature.auth.R
import org.andrewdev.webwallet.feature.auth.RegisterEffect
import org.andrewdev.webwallet.feature.auth.RegisterUiState
import org.andrewdev.webwallet.feature.auth.RegisterViewModel

@Composable
fun RegisterRoute(
    actions: AuthActions,
    onBack: () -> Unit,
    onRegistered: (email: String) -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is RegisterEffect.Registered -> onRegistered(effect.email)
            }
        }
    }
    RegisterScreen(
        state = state,
        actions = actions,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onFirstNameChange = viewModel::onFirstNameChange,
        onLastNameChange = viewModel::onLastNameChange,
        onSubmit = viewModel::submit,
        onBack = onBack,
    )
}

@Composable
fun passwordProblemText(problem: PasswordProblem?): String? = when (problem) {
    PasswordProblem.TOO_SHORT -> stringResource(R.string.feature_auth_password_hint)
    PasswordProblem.TOO_LONG -> stringResource(R.string.feature_auth_password_long)
    null -> null
}

@Composable
fun RegisterScreen(
    state: RegisterUiState,
    actions: AuthActions,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val required = stringResource(R.string.feature_auth_required)
    val emailInvalid = stringResource(R.string.feature_auth_email_invalid)
    AuthScaffold(
        title = stringResource(R.string.feature_auth_register_title),
        subtitle = stringResource(R.string.feature_auth_register_subtitle),
        onToggleLanguage = actions.toggleLanguage,
        onToggleTheme = actions.toggleTheme,
        footer = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_auth_register_have_account),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onBack) { Text(stringResource(R.string.feature_auth_sign_in_link)) }
            }
        },
    ) {
        OutlinedTextField(
            value = state.firstName,
            onValueChange = onFirstNameChange,
            label = { Text(stringResource(R.string.feature_auth_first_name)) },
            isError = state.firstNameMissing || "firstName" in state.fieldErrors,
            supportingText = (state.fieldErrors["firstName"] ?: required.takeIf { state.firstNameMissing })
                ?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.lastName,
            onValueChange = onLastNameChange,
            label = { Text(stringResource(R.string.feature_auth_last_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        EmailField(
            value = state.email,
            onValueChange = onEmailChange,
            error = state.fieldErrors["email"] ?: emailInvalid.takeIf { state.emailInvalid },
        )
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.feature_auth_password),
            error = state.fieldErrors["password"] ?: passwordProblemText(state.passwordProblem),
            supporting = stringResource(R.string.feature_auth_password_hint),
            newPassword = true,
            onDone = onSubmit,
        )
        state.error?.takeIf { state.fieldErrors.isEmpty() }?.let {
            Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            stringResource(R.string.feature_auth_register_agree),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_auth_register_submit))
        }
    }
}

@PreviewLightDark
@Composable
private fun RegisterPreview() {
    WebWalletTheme {
        RegisterScreen(RegisterUiState(password = "short", validated = true), previewActions, {}, {}, {}, {}, {}, {})
    }
}
