package org.andrewdev.webwallet.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.AppLock
import org.andrewdev.webwallet.core.auth.SessionManager
import org.andrewdev.webwallet.core.auth.UnlockResult
import org.andrewdev.webwallet.core.datastore.SessionStore
import javax.inject.Inject

data class LockUiState(
    val pin: String = "",
    val checking: Boolean = false,
    val wrong: Boolean = false,
    val attemptsLeft: Int? = null,
    val cooldownSeconds: Long = 0,
    val biometricEnabled: Boolean = false,
    val displayName: String? = null,
    val signingOut: Boolean = false,
)

/** The lock screen (docs/03 §6.2): PIN entry with the cooldown and the biometric shortcut. */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val lock: AppLock,
    private val session: SessionManager,
    private val store: SessionStore,
) : ViewModel() {

    private val state = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val data = store.current()
            state.update {
                it.copy(
                    biometricEnabled = data.lock.biometric,
                    displayName = data.displayName,
                    cooldownSeconds = secondsLeft(data.lock.cooldownUntilEpochMs),
                )
            }
            if (state.value.cooldownSeconds > 0) countDown()
        }
    }

    fun onDigit(digit: Char) {
        val current = state.value
        if (current.checking || current.cooldownSeconds > 0 || current.pin.length >= AppLock.MAX_PIN_LENGTH) return
        state.update { it.copy(pin = it.pin + digit, wrong = false) }
    }

    /** The unlock button: the PIN length is not stored, so the user says when the PIN is complete. */
    fun submit() {
        val current = state.value
        if (current.checking || current.cooldownSeconds > 0 || current.pin.length < AppLock.MIN_PIN_LENGTH) return
        tryUnlock(current.pin)
    }

    fun onBackspace() = state.update { it.copy(pin = it.pin.dropLast(1), wrong = false) }

    fun onBiometricSuccess() {
        viewModelScope.launch { lock.unlockWithBiometric() }
    }

    fun signOut() {
        state.update { it.copy(signingOut = true) }
        viewModelScope.launch { session.signOut() }
    }

    private fun tryUnlock(pin: String) {
        state.update { it.copy(checking = true) }
        viewModelScope.launch {
            when (val result = lock.unlockWithPin(pin)) {
                UnlockResult.Unlocked -> state.update { it.copy(checking = false, pin = "") }
                is UnlockResult.Wrong -> {
                    state.update {
                        it.copy(
                            checking = false,
                            pin = "",
                            wrong = true,
                            attemptsLeft = result.attemptsLeft,
                            cooldownSeconds = result.cooldownSeconds,
                        )
                    }
                    if (result.cooldownSeconds > 0) countDown()
                }
                UnlockResult.SignOut -> signOut()
            }
        }
    }

    private suspend fun countDown() {
        while (state.value.cooldownSeconds > 0) {
            delay(MS_PER_SECOND)
            state.update { it.copy(cooldownSeconds = it.cooldownSeconds - 1) }
        }
    }

    /** Whole seconds until the instant, zero when it has passed. */
    private fun secondsLeft(untilEpochMs: Long): Long =
        ((untilEpochMs - System.currentTimeMillis()).coerceAtLeast(0) + MS_PER_SECOND - 1) / MS_PER_SECOND

    companion object {
        private const val MS_PER_SECOND = 1_000L
    }
}
