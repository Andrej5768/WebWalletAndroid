package org.andrewdev.webwallet.feature.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.AuthRepository
import org.andrewdev.webwallet.core.data.UserRepository
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.model.User
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.ui.WarnCard
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.message
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** The signed-in user when the account is a demo sandbox, else null. */
@HiltViewModel
class DemoBannerViewModel @Inject constructor(users: UserRepository) : ViewModel() {
    val demoUser: StateFlow<User?> = users.user
        .map { state -> state.dataOrNull?.takeIf { it.demo } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Over the pages of a demo sandbox: when the data disappears and the way to keep it. */
@Composable
fun DemoBanner(onKeep: () -> Unit, viewModel: DemoBannerViewModel = hiltViewModel()) {
    val user by viewModel.demoUser.collectAsStateWithLifecycle()
    val demo = user ?: return
    val f = formatters
    val date = demo.demoExpiresAt?.let { expires ->
        val local = expires.atZone(ZoneId.of("Europe/Kyiv"))
        f.date.short(local.toLocalDate(), f.today) + ", " + local.format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    WarnCard(
        text = if (date != null) {
            stringResource(
                R.string.feature_more_demo_banner,
                date,
            )
        } else {
            stringResource(R.string.feature_more_demo_banner_no_date)
        },
        action = stringResource(R.string.feature_more_demo_keep),
        onAction = onKeep,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    )
}

data class ClaimDemoUiState(
    val firstName: String = "",
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: ApiError? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val done: Boolean = false,
) {
    val canSubmit: Boolean
        get() = firstName.isNotBlank() && email.contains('@') && password.length >= MIN_PASSWORD && !submitting

    companion object {
        const val MIN_PASSWORD = 8
    }
}

/** Keeping the sandbox: email, password and name; the data stays, a verification email goes out. */
@HiltViewModel
class ClaimDemoViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
) : ViewModel() {
    private val state = MutableStateFlow(ClaimDemoUiState())
    val uiState: StateFlow<ClaimDemoUiState> = state.asStateFlow()

    fun onFirstNameChange(value: String) = state.update {
        it.copy(firstName = value, fieldErrors = it.fieldErrors - "firstName")
    }

    fun onEmailChange(value: String) = state.update {
        it.copy(email = value, error = null, fieldErrors = it.fieldErrors - "email")
    }

    fun onPasswordChange(value: String) = state.update {
        it.copy(password = value, fieldErrors = it.fieldErrors - "password")
    }

    fun submit() {
        val current = state.value
        if (!current.canSubmit) return
        state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = auth.claimDemo(current.email, current.password, current.firstName)) {
                is ApiResult.Ok -> {
                    users.refresh()
                    state.update { it.copy(submitting = false, done = true) }
                }
                is ApiResult.Err -> state.update {
                    it.copy(
                        submitting = false,
                        error = result.error,
                        fieldErrors = (result.error as? ApiError.Problem)?.fieldErrors.orEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
fun ClaimDemoRoute(onBack: () -> Unit, viewModel: ClaimDemoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ClaimDemoScreen(
        state = state,
        onFirstNameChange = viewModel::onFirstNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onBack = onBack,
    )
}

@Composable
fun ClaimDemoScreen(
    state: ClaimDemoUiState,
    onFirstNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsLikePage(title = stringResource(R.string.feature_more_demo_keep), onBack = onBack) {
        if (state.done) {
            Text(stringResource(R.string.feature_more_demo_claimed), style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.feature_more_demo_done)) }
            return@SettingsLikePage
        }
        Text(stringResource(R.string.feature_more_demo_claim_text), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.firstName,
            onValueChange = onFirstNameChange,
            label = { Text(stringResource(R.string.feature_more_demo_first_name)) },
            isError = "firstName" in state.fieldErrors,
            supportingText = state.fieldErrors["firstName"]?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.feature_more_demo_email)) },
            isError = "email" in state.fieldErrors,
            supportingText = state.fieldErrors["email"]?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.feature_more_demo_password)) },
            supportingText = {
                Text(
                    state.fieldErrors["password"] ?: stringResource(R.string.feature_more_demo_password_hint),
                )
            },
            isError = "password" in state.fieldErrors,
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.takeIf { state.fieldErrors.isEmpty() }?.let {
            Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_more_demo_keep))
        }
    }
}

/** A pushed page of the More section: a top bar with a back arrow and a scrolling column. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsLikePage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = null) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) { content() }
    }
}
