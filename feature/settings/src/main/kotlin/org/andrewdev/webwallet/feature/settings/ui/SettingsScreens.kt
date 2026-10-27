@file:Suppress("MatchingDeclarationName")

package org.andrewdev.webwallet.feature.settings.ui

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.BadgeCheck
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Shapes
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Wand
import org.andrewdev.webwallet.core.datastore.LanguageSetting
import org.andrewdev.webwallet.core.datastore.ThemeSetting
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.settings.PrivacyViewModel
import org.andrewdev.webwallet.feature.settings.ProfileViewModel
import org.andrewdev.webwallet.feature.settings.R

@Suppress("LongParameterList")
class SettingsActions(
    val back: () -> Unit,
    val openProfile: () -> Unit,
    val openSecurity: () -> Unit,
    val openAppLock: () -> Unit,
    val openPrivacy: () -> Unit,
    val openAbout: () -> Unit,
    val openNotifications: () -> Unit = {},
    val openCategories: () -> Unit = {},
    val openRules: () -> Unit = {},
    val openPayees: () -> Unit = {},
    val openTags: () -> Unit = {},
    val openData: () -> Unit = {},
)

@Composable
private fun SettingsEntry(title: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A pushed settings page: a top bar with a back arrow and a scrolling column. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(title)
            }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.feature_settings_back))
                }
            })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) { content() }
    }
}

/** The settings root (docs/04 §14): the list of sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRootScreen(actions: SettingsActions) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(stringResource(R.string.feature_settings_title))
            }, navigationIcon = {
                IconButton(onClick = actions.back) {
                    Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.feature_settings_back))
                }
            })
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ListItem(headlineContent = {
                Text(stringResource(R.string.feature_settings_profile))
            }, leadingContent = {
                Icon(
                    Lucide.User,
                    null,
                )
            }, modifier = Modifier.clickable(onClick = actions.openProfile))
            ListItem(headlineContent = {
                Text(stringResource(R.string.feature_settings_security))
            }, leadingContent = {
                Icon(
                    Lucide.ShieldCheck,
                    null,
                )
            }, modifier = Modifier.clickable(onClick = actions.openSecurity))
            ListItem(headlineContent = {
                Text(stringResource(R.string.feature_settings_app_lock))
            }, leadingContent = {
                Icon(
                    Lucide.Lock,
                    null,
                )
            }, modifier = Modifier.clickable(onClick = actions.openAppLock))
            SettingsEntry(R.string.feature_settings_notifications, Lucide.Bell, actions.openNotifications)
            SettingsEntry(R.string.feature_settings_categories, Lucide.Shapes, actions.openCategories)
            SettingsEntry(R.string.feature_settings_rules, Lucide.Wand, actions.openRules)
            SettingsEntry(R.string.feature_settings_payees, Lucide.Store, actions.openPayees)
            SettingsEntry(R.string.feature_settings_tags, Lucide.Tag, actions.openTags)
            SettingsEntry(R.string.feature_settings_data, Lucide.Database, actions.openData)
            ListItem(headlineContent = {
                Text(stringResource(R.string.feature_settings_privacy))
            }, leadingContent = {
                Icon(
                    Lucide.Shield,
                    null,
                )
            }, modifier = Modifier.clickable(onClick = actions.openPrivacy))
            ListItem(headlineContent = {
                Text(stringResource(R.string.feature_settings_about))
            }, leadingContent = { Icon(Lucide.Info, null) }, modifier = Modifier.clickable(onClick = actions.openAbout))
        }
    }
}

@Composable
fun ProfileRoute(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickCurrency by remember { mutableStateOf(false) }
    SettingsPage(title = stringResource(R.string.feature_settings_profile), onBack = onBack) {
        state.user?.let { user ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.email, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (user.emailVerified) {
                    Icon(
                        Lucide.BadgeCheck,
                        contentDescription = stringResource(R.string.feature_settings_verified),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.firstName,
            onValueChange = viewModel::onFirstNameChange,
            label = { Text(stringResource(R.string.feature_settings_first_name)) },
            isError = "firstName" in state.fieldErrors || (state.loaded && state.firstName.isBlank()),
            supportingText = state.fieldErrors["firstName"]?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.lastName,
            onValueChange = viewModel::onLastNameChange,
            label = { Text(stringResource(R.string.feature_settings_last_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.baseCurrency,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(stringResource(R.string.feature_settings_base_currency)) },
            supportingText = { Text(stringResource(R.string.feature_settings_base_currency_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { pickCurrency = true },
        )
        state.error?.takeIf {
            state.fieldErrors.isEmpty()
        }?.let {
            Text(
                it.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.saved) {
            Text(
                stringResource(R.string.feature_settings_saved),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = viewModel::save,
            enabled = state.dirty && !state.saving && state.firstName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.feature_settings_save))
        }
        Text(stringResource(R.string.feature_settings_language), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LanguageSetting.entries.forEach { language ->
                FilterChip(selected = state.language == language, onClick = {
                    viewModel.onLanguageChange(language)
                }, label = { Text(languageName(language)) })
            }
        }
        Text(stringResource(R.string.feature_settings_theme), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ThemeSetting.entries.forEach { theme ->
                FilterChip(selected = state.theme == theme, onClick = {
                    viewModel.onThemeChange(theme)
                }, label = { Text(themeName(theme)) })
            }
        }
    }
    if (pickCurrency) {
        CurrencyPicker(onPick = {
            viewModel.onBaseCurrencyChange(it)
            pickCurrency = false
        }, onDismiss = { pickCurrency = false })
    }
}

@Composable
fun languageName(language: LanguageSetting): String = stringResource(
    when (language) {
        LanguageSetting.SYSTEM -> R.string.feature_settings_language_system
        LanguageSetting.UK -> R.string.feature_settings_language_uk
        LanguageSetting.EN -> R.string.feature_settings_language_en
    },
)

@Composable
fun themeName(theme: ThemeSetting): String = stringResource(
    when (theme) {
        ThemeSetting.SYSTEM -> R.string.feature_settings_theme_system
        ThemeSetting.LIGHT -> R.string.feature_settings_theme_light
        ThemeSetting.DARK -> R.string.feature_settings_theme_dark
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val codes = remember { listOf("UAH", "USD", "EUR", "GBP", "PLN", "CZK", "CHF", "CAD", "GEL", "TRY", "KZT") }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        codes.forEach { code ->
            ListItem(
                headlineContent = { Text(code) },
                modifier = Modifier.clickable { onPick(code) },
            )
        }
    }
}

@Composable
fun PrivacyRoute(onBack: () -> Unit, viewModel: PrivacyViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsPage(title = stringResource(R.string.feature_settings_privacy), onBack = onBack) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.feature_settings_telemetry), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.feature_settings_telemetry_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = data.telemetryConsent == true, onCheckedChange = viewModel::setTelemetry)
        }
        androidx.compose.material3.TextButton(onClick = {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(PRIVACY_URL))
        }) {
            Text(stringResource(R.string.feature_settings_privacy_policy))
        }
    }
}

const val PRIVACY_URL = "https://wallet.andrew-dev.org/privacy"
const val TERMS_URL = "https://wallet.andrew-dev.org/terms"
const val SUPPORT_EMAIL = "support@andrew-dev.org"

/** Version, backend (dev only), legal pages, support (docs/04 §14). */
@Composable
fun AboutScreen(versionName: String, versionCode: Int, backendUrl: String?, storeUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(title = stringResource(R.string.feature_settings_about), onBack = onBack) {
        Text("WebWallet $versionName ($versionCode)", style = MaterialTheme.typography.bodyLarge)
        if (backendUrl != null) {
            Text(
                stringResource(R.string.feature_settings_backend, backendUrl),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.feature_settings_terms))
            },
            modifier = Modifier.clickable {
                CustomTabsIntent.Builder().build().launchUrl(
                    context,
                    Uri.parse(TERMS_URL),
                )
            },
        )
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.feature_settings_privacy_policy))
            },
            modifier = Modifier.clickable {
                CustomTabsIntent.Builder().build().launchUrl(
                    context,
                    Uri.parse(PRIVACY_URL),
                )
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_support)) },
            supportingContent = { Text(SUPPORT_EMAIL) },
            modifier = Modifier.clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))) }
            },
        )
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.feature_settings_rate))
            },
            modifier = Modifier.clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl))) }
            },
        )
    }
}

/** A password field of the settings forms. */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (numeric) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Password,
            imeAction = imeAction,
        ),
        modifier = modifier.fillMaxWidth().padding(0.dp),
    )
}
