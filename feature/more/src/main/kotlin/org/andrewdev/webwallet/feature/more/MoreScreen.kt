package org.andrewdev.webwallet.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composables.icons.lucide.BadgeCheck
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.Import
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.TrendingUp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.AppLock
import org.andrewdev.webwallet.core.auth.SessionManager
import org.andrewdev.webwallet.core.data.UserRepository
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.User
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import javax.inject.Inject

data class MoreUiState(val user: User? = null, val lockEnabled: Boolean = false, val signingOut: Boolean = false)

@HiltViewModel
class MoreViewModel @Inject constructor(
    users: UserRepository,
    private val lock: AppLock,
    private val session: SessionManager,
) : ViewModel() {

    val uiState: StateFlow<MoreUiState> = combine(users.user, lock.settings) { user, lockData ->
        MoreUiState(user = user.dataOrNull, lockEnabled = lockData.enabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MoreUiState())

    fun lockNow() {
        viewModelScope.launch { lock.lockNow() }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Sections of later milestones, shown as "coming soon" in the hub. */
enum class MoreSection { REPORTS, GOALS, INVESTMENTS, TAXES, IMPORTS }

class MoreActions(val openSettings: () -> Unit, val openAbout: () -> Unit, val openSection: (MoreSection) -> Unit)

@Composable
fun MoreRoute(actions: MoreActions, viewModel: MoreViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MoreScreen(state, actions, onLock = viewModel::lockNow, onSignOut = viewModel::signOut)
}

/** The "More" hub (docs/04 §8): the profile header, the sections, lock and sign out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(state: MoreUiState, actions: MoreActions, onLock: () -> Unit, onSignOut: () -> Unit) {
    var confirmSignOut by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_more_title)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            state.user?.let { Header(it) }
            Entry(
                Lucide.ChartColumn,
                stringResource(R.string.feature_more_reports),
                stringResource(R.string.feature_more_reports_hint),
            ) { actions.openSection(MoreSection.REPORTS) }
            Entry(
                Lucide.Target,
                stringResource(R.string.feature_more_goals),
                stringResource(R.string.feature_more_goals_hint),
            ) { actions.openSection(MoreSection.GOALS) }
            Entry(
                Lucide.TrendingUp,
                stringResource(R.string.feature_more_investments),
                stringResource(R.string.feature_more_investments_hint),
            ) { actions.openSection(MoreSection.INVESTMENTS) }
            Entry(
                Lucide.Receipt,
                stringResource(R.string.feature_more_taxes),
                stringResource(R.string.feature_more_taxes_hint),
            ) { actions.openSection(MoreSection.TAXES) }
            Entry(
                Lucide.Import,
                stringResource(R.string.feature_more_imports),
                stringResource(R.string.feature_more_imports_hint),
            ) { actions.openSection(MoreSection.IMPORTS) }
            Entry(
                Lucide.Settings,
                stringResource(R.string.feature_more_settings),
                stringResource(R.string.feature_more_settings_hint),
                actions.openSettings,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
            if (state.lockEnabled) Entry(Lucide.Lock, stringResource(R.string.feature_more_lock_now), null, onLock)
            Entry(Lucide.Info, stringResource(R.string.feature_more_about), null, actions.openAbout)
            Entry(Lucide.LogOut, stringResource(R.string.feature_more_sign_out), null) { confirmSignOut = true }
        }
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.feature_more_sign_out_title),
            confirmText = stringResource(R.string.feature_more_sign_out),
            destructive = true,
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

@Composable
private fun Header(user: User) {
    Row(modifier = Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                user.initials,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(modifier = Modifier.padding(start = Spacing.md).weight(1f)) {
            Text(user.fullName, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (user.emailVerified) {
                    Icon(
                        Lucide.BadgeCheck,
                        contentDescription = stringResource(R.string.feature_more_verified),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.xs).size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Entry(icon: ImageVector, title: String, hint: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = hint?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@PreviewLightDark
@Composable
private fun MorePreview() {
    WebWalletTheme {
        MoreScreen(
            MoreUiState(
                User(1, "ada@example.com", true, "Ada", "Lovelace", CurrencyCode.UAH, "uk", true, false, 0, null),
                lockEnabled = true,
            ),
            MoreActions({}, {}, {}),
            {},
            {},
        )
    }
}
