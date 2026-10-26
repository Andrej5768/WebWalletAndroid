package org.andrewdev.webwallet.ui

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import org.andrewdev.webwallet.R
import org.andrewdev.webwallet.core.auth.AuthState
import org.andrewdev.webwallet.core.auth.SignOutReason
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.datastore.ThemeSetting
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.navigation.DeepLink
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ProvideFormatters
import org.andrewdev.webwallet.feature.auth.ui.AuthActions
import org.andrewdev.webwallet.feature.auth.ui.AuthNavHost
import org.andrewdev.webwallet.feature.auth.ui.ConsentScreen
import org.andrewdev.webwallet.feature.auth.ui.SignInNotice
import org.andrewdev.webwallet.feature.lock.ui.LockOfferScreen
import org.andrewdev.webwallet.feature.lock.ui.LockRoute
import org.andrewdev.webwallet.feature.lock.ui.PinSetupRoute

/**
 * The root (docs/02 §7): the auth graph when signed out, the lock over everything when locked, the consent and the
 * lock offer after the first sign-in, then the main scaffold. The pending deep link is handed to whichever graph
 * can show it.
 */
@Composable
fun RootScreen(deepLink: DeepLink?, onDeepLinkConsumed: () -> Unit, viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.theme) {
        ThemeSetting.SYSTEM -> systemDark
        ThemeSetting.DARK -> true
        ThemeSetting.LIGHT -> false
    }
    val language = LanguageResolver.resolve(state.language)
    LocalizedContent(language.locale.toLanguageTag()) {
        ProvideFormatters(language = language, today = remember { KyivClock() }.today()) {
            WebWalletTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RootContent(state, deepLink, onDeepLinkConsumed, viewModel, systemDark)
                }
            }
        }
    }
}

@Composable
private fun RootContent(
    state: RootUiState,
    deepLink: DeepLink?,
    onDeepLinkConsumed: () -> Unit,
    viewModel: RootViewModel,
    systemDark: Boolean,
) {
    val context = LocalContext.current
    var settingUpPin by rememberSaveable { mutableStateOf(false) }
    state.updateRequired?.let { config ->
        EmptyState(
            title = stringResource(R.string.app_update_required_title),
            body = stringResource(R.string.app_update_required_text),
            action = stringResource(R.string.app_update_required_action),
            onAction = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, config.storeUrl.toUri()),
                    )
                }
            },
            modifier = Modifier.padding(top = 120.dp),
        )
        return
    }
    when (val auth = state.auth) {
        AuthState.Unknown -> Unit
        is AuthState.SignedOut -> {
            val publicKey: NavKey? = (deepLink as? DeepLink.Public)?.key
            AuthNavHost(
                start = publicKey,
                initialNotice = SignInNotice.SESSION_EXPIRED.takeIf { auth.reason == SignOutReason.SESSION_EXPIRED },
                onToggleLanguage = viewModel::toggleLanguage,
                onToggleTheme = { viewModel.toggleTheme(systemDark) },
            )
        }
        is AuthState.SignedIn -> when {
            state.locked -> LockRoute()
            state.needsConsent -> ConsentScreen(
                onAnswer = viewModel::answerConsent,
                actions = AuthActions(
                    viewModel::toggleLanguage,
                    { viewModel.toggleTheme(systemDark) },
                    {},
                    {},
                    {},
                    { _, _ -> },
                    {},
                ),
            )
            settingUpPin -> PinSetupRoute(change = false, onClose = { settingUpPin = false })
            state.needsLockOffer -> LockOfferScreen(
                onSetUp = { settingUpPin = true },
                onNotNow = viewModel::declineLockOffer,
            )
            else -> MainScaffold(deepLink = deepLink as? DeepLink.Main, onDeepLinkConsumed = onDeepLinkConsumed)
        }
    }
}

/** Resources in the chosen language on every Android version: the context is re-created with that locale. */
@Composable
fun LocalizedContent(languageTag: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(base, languageTag, configuration) {
        val config = Configuration(configuration).apply { setLocale(java.util.Locale.forLanguageTag(languageTag)) }
        base.createConfigurationContext(config) to config
    }
    CompositionLocalProvider(
        LocalContext provides localized.first,
        LocalConfiguration provides localized.second,
        content = content,
    )
}
