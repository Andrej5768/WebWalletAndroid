package org.andrewdev.webwallet.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.AppLock
import org.andrewdev.webwallet.core.data.TwoFactorSetup
import org.andrewdev.webwallet.core.data.UserRepository
import org.andrewdev.webwallet.core.datastore.LanguageSetting
import org.andrewdev.webwallet.core.datastore.LockData
import org.andrewdev.webwallet.core.datastore.SettingsData
import org.andrewdev.webwallet.core.datastore.SettingsStore
import org.andrewdev.webwallet.core.datastore.ThemeSetting
import org.andrewdev.webwallet.core.model.User
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

// --- Profile (docs/04 §14)

data class ProfileUiState(
    val loaded: Boolean = false,
    val user: User? = null,
    val firstName: String = "",
    val lastName: String = "",
    val baseCurrency: String = "UAH",
    val language: LanguageSetting = LanguageSetting.SYSTEM,
    val theme: ThemeSetting = ThemeSetting.SYSTEM,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: ApiError? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val dirty: Boolean
        get() = user != null && (
            firstName != user.firstName ||
                lastName != user.lastName.orEmpty() ||
                baseCurrency != user.baseCurrency.code
            )
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UserRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val state = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = users.user.first { it.dataOrNull != null }.dataOrNull!!
            val prefs = settings.current()
            state.update {
                it.copy(
                    loaded = true,
                    user = user,
                    firstName = user.firstName,
                    lastName = user.lastName.orEmpty(),
                    baseCurrency = user.baseCurrency.code,
                    language = prefs.language,
                    theme = prefs.theme,
                )
            }
        }
    }

    fun onFirstNameChange(value: String) = state.update {
        it.copy(
            firstName = value,
            saved = false,
            fieldErrors = it.fieldErrors - "firstName",
        )
    }

    fun onLastNameChange(value: String) = state.update { it.copy(lastName = value, saved = false) }

    fun onBaseCurrencyChange(code: String) = state.update { it.copy(baseCurrency = code, saved = false) }

    /** Language and theme are device settings, saved at once; the language also goes to the profile's `locale`. */
    fun onLanguageChange(language: LanguageSetting) {
        state.update { it.copy(language = language) }
        viewModelScope.launch {
            settings.update { it.copy(language = language) }
            val user = state.value.user ?: return@launch
            val locale = when (language) {
                LanguageSetting.UK -> "uk"
                LanguageSetting.EN -> "en"
                LanguageSetting.SYSTEM -> return@launch
            }
            if (locale != user.locale) {
                users.updateProfile(
                    user.firstName,
                    user.lastName,
                    user.baseCurrency.code,
                    locale,
                )
            }
        }
    }

    fun onThemeChange(theme: ThemeSetting) {
        state.update { it.copy(theme = theme) }
        viewModelScope.launch { settings.update { it.copy(theme = theme) } }
    }

    fun save() {
        val current = state.value
        val user = current.user ?: return
        if (current.firstName.isBlank() || current.saving) return
        state.update { it.copy(saving = true, error = null, saved = false) }
        viewModelScope.launch {
            when (
                val result = users.updateProfile(
                    current.firstName,
                    current.lastName,
                    current.baseCurrency,
                    user.locale,
                )
            ) {
                is ApiResult.Ok -> state.update { it.copy(saving = false, saved = true, user = result.value) }
                is ApiResult.Err -> state.update {
                    it.copy(
                        saving = false,
                        error = result.error,
                        fieldErrors = (result.error as? ApiError.Problem)?.fieldErrors.orEmpty(),
                    )
                }
            }
        }
    }
}

// --- Security (docs/03 §5, docs/04 §14)

data class SecurityUiState(
    val user: User? = null,
    val lock: LockData = LockData(),
    val currentPassword: String = "",
    val newPassword: String = "",
    val repeatPassword: String = "",
    val changingPassword: Boolean = false,
    val passwordChanged: Boolean = false,
    val passwordError: ApiError? = null,
    /** The 2FA flow: confirming identity, then the secret, then the code. */
    val twoFactorStep: TwoFactorStep = TwoFactorStep.NONE,
    val setup: TwoFactorSetup? = null,
    val identityPassword: String = "",
    val identityCode: String = "",
    val code: String = "",
    val working: Boolean = false,
    val twoFactorError: ApiError? = null,
    val recoveryCodes: List<String>? = null,
)

enum class TwoFactorStep { NONE, CONFIRM_IDENTITY, SCAN, DISABLE, REGENERATE }

@Suppress("TooManyFunctions")
@HiltViewModel
class SecurityViewModel @Inject constructor(private val users: UserRepository, lock: AppLock) : ViewModel() {

    private val form = MutableStateFlow(SecurityUiState())

    val uiState: StateFlow<SecurityUiState> = combine(form, users.user, lock.settings) { current, user, lockData ->
        current.copy(user = user.dataOrNull, lock = lockData)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SecurityUiState())

    fun onCurrentPasswordChange(value: String) = form.update {
        it.copy(
            currentPassword = value,
            passwordError = null,
            passwordChanged = false,
        )
    }

    fun onNewPasswordChange(value: String) = form.update {
        it.copy(
            newPassword = value,
            passwordError = null,
            passwordChanged = false,
        )
    }

    fun onRepeatPasswordChange(value: String) = form.update {
        it.copy(
            repeatPassword = value,
            passwordError = null,
            passwordChanged = false,
        )
    }

    fun changePassword() {
        val current = form.value
        if (current.changingPassword || current.newPassword != current.repeatPassword) return
        form.update { it.copy(changingPassword = true, passwordError = null) }
        viewModelScope.launch {
            when (val result = users.changePassword(current.currentPassword, current.newPassword)) {
                is ApiResult.Ok -> form.update {
                    it.copy(
                        changingPassword = false,
                        passwordChanged = true,
                        currentPassword = "",
                        newPassword = "",
                        repeatPassword = "",
                    )
                }
                is ApiResult.Err -> form.update { it.copy(changingPassword = false, passwordError = result.error) }
            }
        }
    }

    fun startSetup() = form.update {
        it.copy(
            twoFactorStep = TwoFactorStep.CONFIRM_IDENTITY,
            identityPassword = "",
            identityCode = "",
            twoFactorError = null,
        )
    }

    fun startDisable() = form.update {
        it.copy(
            twoFactorStep = TwoFactorStep.DISABLE,
            identityPassword = "",
            code = "",
            twoFactorError = null,
        )
    }

    fun startRegenerate() = form.update {
        it.copy(
            twoFactorStep = TwoFactorStep.REGENERATE,
            code = "",
            twoFactorError = null,
        )
    }

    fun cancelTwoFactor() = form.update {
        it.copy(
            twoFactorStep = TwoFactorStep.NONE,
            setup = null,
            code = "",
            recoveryCodes = null,
            twoFactorError = null,
        )
    }

    fun onIdentityPasswordChange(value: String) = form.update {
        it.copy(
            identityPassword = value,
            twoFactorError = null,
        )
    }

    fun onIdentityCodeChange(value: String) = form.update { it.copy(identityCode = value, twoFactorError = null) }

    fun onCodeChange(value: String) = form.update { it.copy(code = value, twoFactorError = null) }

    /** Confirm identity → `POST /me/two-factor/setup` → the QR step. */
    fun confirmIdentity() {
        val current = form.value
        val hasPassword = uiState.value.user?.hasPassword ?: true
        form.update { it.copy(working = true, twoFactorError = null) }
        viewModelScope.launch {
            val result = users.setupTwoFactor(
                password = current.identityPassword.takeIf { hasPassword && it.isNotEmpty() },
                code = current.identityCode.takeIf { !hasPassword && it.isNotEmpty() },
            )
            when (result) {
                is ApiResult.Ok -> form.update {
                    it.copy(
                        working = false,
                        setup = result.value,
                        twoFactorStep = TwoFactorStep.SCAN,
                        code = "",
                    )
                }
                is ApiResult.Err -> form.update { it.copy(working = false, twoFactorError = result.error) }
            }
        }
    }

    fun enable() {
        val code = form.value.code
        if (code.length < CODE_LENGTH) return
        form.update { it.copy(working = true, twoFactorError = null) }
        viewModelScope.launch {
            when (val result = users.enableTwoFactor(code)) {
                is ApiResult.Ok -> form.update {
                    it.copy(
                        working = false,
                        recoveryCodes = result.value,
                        twoFactorStep = TwoFactorStep.NONE,
                        setup = null,
                        code = "",
                    )
                }
                is ApiResult.Err -> form.update { it.copy(working = false, twoFactorError = result.error, code = "") }
            }
        }
    }

    fun disable() {
        val current = form.value
        val hasPassword = uiState.value.user?.hasPassword ?: true
        if (current.code.isBlank()) return
        form.update { it.copy(working = true, twoFactorError = null) }
        viewModelScope.launch {
            when (val result = users.disableTwoFactor(current.code, current.identityPassword.takeIf { hasPassword })) {
                is ApiResult.Ok -> form.update {
                    it.copy(
                        working = false,
                        twoFactorStep = TwoFactorStep.NONE,
                        code = "",
                        identityPassword = "",
                    )
                }
                is ApiResult.Err -> form.update { it.copy(working = false, twoFactorError = result.error) }
            }
        }
    }

    fun regenerate() {
        val code = form.value.code
        if (code.isBlank()) return
        form.update { it.copy(working = true, twoFactorError = null) }
        viewModelScope.launch {
            when (val result = users.regenerateRecoveryCodes(code)) {
                is ApiResult.Ok -> form.update {
                    it.copy(
                        working = false,
                        recoveryCodes = result.value,
                        twoFactorStep = TwoFactorStep.NONE,
                        code = "",
                    )
                }
                is ApiResult.Err -> form.update { it.copy(working = false, twoFactorError = result.error) }
            }
        }
    }

    fun codesSaved() = form.update { it.copy(recoveryCodes = null) }

    companion object {
        const val CODE_LENGTH = 6
    }
}

// --- App lock (docs/03 §6.3)

@HiltViewModel
class AppLockSettingsViewModel @Inject constructor(private val lock: AppLock) : ViewModel() {

    val settings: StateFlow<LockData> = lock.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        LockData(),
    )

    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch { lock.setBiometric(enabled) }
    }

    fun setTimeout(minutes: Int) {
        viewModelScope.launch { lock.setTimeout(minutes) }
    }

    fun setShowBalancesInWidgets(show: Boolean) {
        viewModelScope.launch { lock.setShowBalancesInWidgets(show) }
    }

    /** Turning off asks the current PIN first (docs/03 §6.3). */
    fun disable(pin: String, onWrong: () -> Unit) {
        viewModelScope.launch { if (lock.verifyPin(pin)) lock.disable() else onWrong() }
    }
}

// --- Privacy (docs/08 §6)

@HiltViewModel
class PrivacyViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {

    val data: StateFlow<SettingsData> = settings.data.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        SettingsData(),
    )

    fun setTelemetry(enabled: Boolean) {
        viewModelScope.launch { settings.update { it.copy(telemetryConsent = enabled) } }
    }
}
