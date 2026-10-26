package org.andrewdev.webwallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.AppLock
import org.andrewdev.webwallet.core.auth.AuthState
import org.andrewdev.webwallet.core.auth.SessionManager
import org.andrewdev.webwallet.core.common.UiLanguage
import org.andrewdev.webwallet.core.data.AppConfigRepository
import org.andrewdev.webwallet.core.datastore.LanguageSetting
import org.andrewdev.webwallet.core.datastore.SessionStore
import org.andrewdev.webwallet.core.datastore.SettingsStore
import org.andrewdev.webwallet.core.datastore.ThemeSetting
import org.andrewdev.webwallet.core.model.AppConfig
import org.andrewdev.webwallet.feature.overview.VersionCode
import javax.inject.Inject

/** What the root composable switches on (docs/02 §7): the session, the lock, the first-run screens, the theme. */
data class RootUiState(
    val auth: AuthState = AuthState.Unknown,
    val locked: Boolean = false,
    /** The lock is set up, so FLAG_SECURE is on whatever the state (docs/03 §6.2). */
    val lockEnabled: Boolean = false,
    /** The consent screen was not answered yet. */
    val needsConsent: Boolean = false,
    /** The lock offer was not shown yet on this device. */
    val needsLockOffer: Boolean = false,
    val theme: ThemeSetting = ThemeSetting.SYSTEM,
    val language: LanguageSetting = LanguageSetting.SYSTEM,
    /** The app is older than the backend accepts (B8): a blocking screen. */
    val updateRequired: AppConfig? = null,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val session: SessionManager,
    private val lock: AppLock,
    private val settings: SettingsStore,
    private val sessionStore: SessionStore,
    private val appConfig: AppConfigRepository,
    private val versionCode: VersionCode,
) : ViewModel() {

    val uiState: StateFlow<RootUiState> = combine(
        session.authState,
        lock.locked,
        settings.data,
        sessionStore.lock,
        appConfig.config,
    ) { auth, locked, prefs, lockData, config ->
        RootUiState(
            auth = auth,
            locked = locked && lockData.enabled,
            lockEnabled = lockData.enabled,
            needsConsent = prefs.telemetryConsent == null,
            needsLockOffer = !lockData.offered && !lockData.enabled,
            theme = prefs.theme,
            language = prefs.language,
            updateRequired = config?.takeIf { it.minSupportedVersionCode > versionCode.value },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootUiState())

    init {
        viewModelScope.launch {
            lock.onStart()
            session.restore()
            appConfig.refreshIfDue()
        }
    }

    fun onBackground() = lock.onBackground()

    fun onForeground() {
        viewModelScope.launch {
            lock.onForeground()
            val signedIn = session.authState.value is AuthState.SignedIn
            if (signedIn && session.accessTokenEndsSoon()) session.refreshInBackground()
        }
    }

    fun answerConsent(allow: Boolean) {
        viewModelScope.launch { settings.update { it.copy(telemetryConsent = allow) } }
    }

    fun declineLockOffer() {
        viewModelScope.launch { lock.markOffered() }
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            settings.update {
                val next = when (LanguageResolver.resolve(it.language)) {
                    UiLanguage.UK -> LanguageSetting.EN
                    UiLanguage.EN -> LanguageSetting.UK
                }
                it.copy(language = next)
            }
        }
    }

    fun toggleTheme(systemDark: Boolean) {
        viewModelScope.launch {
            settings.update {
                val dark = when (it.theme) {
                    ThemeSetting.SYSTEM -> systemDark
                    ThemeSetting.DARK -> true
                    ThemeSetting.LIGHT -> false
                }
                it.copy(theme = if (dark) ThemeSetting.LIGHT else ThemeSetting.DARK)
            }
        }
    }
}
