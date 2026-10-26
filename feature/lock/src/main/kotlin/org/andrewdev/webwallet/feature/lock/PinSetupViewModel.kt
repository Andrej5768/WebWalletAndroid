package org.andrewdev.webwallet.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.AppLock
import org.andrewdev.webwallet.core.security.PinHasher
import javax.inject.Inject

/** The steps of the PIN setup (docs/03 §6.1). */
enum class PinSetupStep { CURRENT, CREATE, REPEAT, OPTIONS }

data class PinSetupUiState(
    val step: PinSetupStep = PinSetupStep.CREATE,
    val pin: String = "",
    val first: String = "",
    val weakWarning: Boolean = false,
    val mismatch: Boolean = false,
    val wrongCurrent: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometric: Boolean = true,
    val timeoutMinutes: Int = 0,
    val saving: Boolean = false,
)

@HiltViewModel
class PinSetupViewModel @Inject constructor(private val lock: AppLock) : ViewModel() {

    private val state = MutableStateFlow(PinSetupUiState())
    val uiState: StateFlow<PinSetupUiState> = state.asStateFlow()

    /** CHANGE mode starts by asking the current PIN; biometric availability comes from the screen. */
    fun start(change: Boolean, biometricAvailable: Boolean) {
        state.update {
            it.copy(
                step = if (change) PinSetupStep.CURRENT else PinSetupStep.CREATE,
                biometricAvailable = biometricAvailable,
                biometric = biometricAvailable,
            )
        }
    }

    fun onDigit(digit: Char) {
        val current = state.value
        if (current.saving || current.pin.length >= AppLock.MAX_PIN_LENGTH) return
        state.update { it.copy(pin = it.pin + digit, mismatch = false, wrongCurrent = false) }
    }

    fun onBackspace() = state.update { it.copy(pin = it.pin.dropLast(1)) }

    /** "Next" after a PIN of 4–8 digits; the weak warning can be overridden by pressing again. */
    fun next() {
        val current = state.value
        if (current.pin.length < AppLock.MIN_PIN_LENGTH) return
        when (current.step) {
            PinSetupStep.CURRENT -> viewModelScope.launch {
                if (lock.verifyPin(current.pin)) {
                    state.update { it.copy(step = PinSetupStep.CREATE, pin = "") }
                } else {
                    state.update { it.copy(pin = "", wrongCurrent = true) }
                }
            }
            PinSetupStep.CREATE -> if (PinHasher.isWeak(current.pin) && !current.weakWarning) {
                state.update { it.copy(weakWarning = true) }
            } else {
                state.update { it.copy(step = PinSetupStep.REPEAT, first = it.pin, pin = "", weakWarning = false) }
            }
            PinSetupStep.REPEAT -> if (current.pin == current.first) {
                state.update { it.copy(step = PinSetupStep.OPTIONS, pin = "") }
            } else {
                state.update { it.copy(pin = "", mismatch = true) }
            }
            PinSetupStep.OPTIONS -> Unit
        }
    }

    fun onBiometricChange(enabled: Boolean) = state.update { it.copy(biometric = enabled) }

    fun onTimeoutChange(minutes: Int) = state.update { it.copy(timeoutMinutes = minutes) }

    /** Saves the lock; [change] keeps the existing options and only replaces the PIN. */
    fun save(change: Boolean, onDone: () -> Unit) {
        val current = state.value
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (change) {
                lock.changePin(
                    current.first,
                )
            } else {
                lock.enable(current.first, current.biometric, current.timeoutMinutes)
            }
            onDone()
        }
    }
}
