package org.andrewdev.webwallet.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.AuthRepository
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.ErrorCode
import javax.inject.Inject

data class TwoFactorUiState(
    val code: String = "",
    val recoveryCode: String = "",
    val useRecovery: Boolean = false,
    val trustDevice: Boolean = false,
    val submitting: Boolean = false,
    val error: ApiError? = null,
    /** A wrong code shakes the field and clears it. */
    val wrongCode: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !submitting && codeComplete

    private val codeComplete: Boolean
        get() = if (useRecovery) recoveryCode.trim().length >= RECOVERY_MIN else code.length == CODE_LENGTH

    companion object {
        const val CODE_LENGTH = 6
        private const val RECOVERY_MIN = 8
    }
}

sealed interface TwoFactorEffect {
    /** The challenge is over (5 minutes): back to the sign-in with a note. */
    data object Expired : TwoFactorEffect
}

@HiltViewModel
class TwoFactorViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

    private val state = MutableStateFlow(TwoFactorUiState())
    private val effects = Channel<TwoFactorEffect>(Channel.BUFFERED)

    val uiState: StateFlow<TwoFactorUiState> = state.asStateFlow()
    val effect = effects.receiveAsFlow()

    fun onCodeChange(code: String) = state.update { it.copy(code = code, error = null, wrongCode = false) }

    fun onRecoveryCodeChange(code: String) = state.update {
        it.copy(
            recoveryCode = code,
            error = null,
            wrongCode = false,
        )
    }

    fun onTrustDeviceChange(trust: Boolean) = state.update { it.copy(trustDevice = trust) }

    fun toggleRecovery() = state.update {
        it.copy(
            useRecovery = !it.useRecovery,
            code = "",
            recoveryCode = "",
            error = null,
            wrongCode = false,
        )
    }

    fun submit(challenge: String) {
        val current = state.value
        if (!current.canSubmit) return
        state.update { it.copy(submitting = true, error = null) }
        val code = if (current.useRecovery) current.recoveryCode.trim().uppercase() else current.code
        viewModelScope.launch {
            when (val result = auth.confirmTwoFactor(challenge, code, current.trustDevice)) {
                is ApiResult.Ok -> state.update { it.copy(submitting = false) }
                is ApiResult.Err -> {
                    val problem = result.error as? ApiError.Problem
                    when (problem?.code) {
                        ErrorCode.TWO_FACTOR_EXPIRED -> effects.send(TwoFactorEffect.Expired)
                        ErrorCode.INVALID_TWO_FACTOR_CODE ->
                            state.update {
                                it.copy(
                                    submitting = false,
                                    error = result.error,
                                    wrongCode = true,
                                    code = "",
                                    recoveryCode = "",
                                )
                            }
                        else -> state.update { it.copy(submitting = false, error = result.error) }
                    }
                }
            }
        }
    }
}
