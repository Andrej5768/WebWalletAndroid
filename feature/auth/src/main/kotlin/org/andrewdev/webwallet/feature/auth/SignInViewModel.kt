package org.andrewdev.webwallet.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.AuthRepository
import org.andrewdev.webwallet.core.auth.SignInOutcome
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.ErrorCode
import javax.inject.Inject

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    /** The backend's own message under the form; field errors go next to their field. */
    val error: ApiError? = null,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    /** The button is disabled this many seconds after a 429 (docs/03 §3.1). */
    val cooldownSeconds: Int = 0,
    /** EMAIL_NOT_VERIFIED: the error shows a "Resend" action. */
    val notVerified: Boolean = false,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !submitting && cooldownSeconds == 0
}

sealed interface SignInEffect {
    data class TwoFactor(val challenge: String) : SignInEffect

    data class PendingDeletion(val deletionScheduledAt: String, val restoreToken: String) : SignInEffect

    data class CheckMail(val email: String) : SignInEffect
}

@HiltViewModel
class SignInViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

    private val state = MutableStateFlow(SignInUiState())
    private val effects = Channel<SignInEffect>(Channel.BUFFERED)

    val uiState: StateFlow<SignInUiState> = state.asStateFlow()
    val effect = effects.receiveAsFlow()

    fun onEmailChange(email: String) = state.update {
        it.copy(
            email = email,
            emailError = false,
            error = null,
            notVerified = false,
        )
    }

    fun onPasswordChange(password: String) = state.update {
        it.copy(
            password = password,
            passwordError = false,
            error = null,
        )
    }

    fun prefill(email: String) {
        if (state.value.email.isEmpty()) state.update { it.copy(email = email) }
    }

    fun submit() {
        val current = state.value
        if (!current.canSubmit) return
        state.update { it.copy(submitting = true, error = null, notVerified = false) }
        viewModelScope.launch {
            when (val result = auth.signIn(current.email, current.password)) {
                is ApiResult.Ok -> when (val outcome = result.value) {
                    SignInOutcome.SignedIn -> state.update { it.copy(submitting = false) }
                    is SignInOutcome.TwoFactorRequired -> {
                        state.update { it.copy(submitting = false, password = "") }
                        effects.send(SignInEffect.TwoFactor(outcome.challenge))
                    }
                }
                is ApiResult.Err -> fail(result.error)
            }
        }
    }

    fun resendVerification() {
        val email = state.value.email
        viewModelScope.launch {
            auth.resendVerification(email)
            effects.send(SignInEffect.CheckMail(email))
        }
    }

    private suspend fun fail(error: ApiError) {
        val problem = error as? ApiError.Problem
        when (problem?.code) {
            ErrorCode.ACCOUNT_DELETION_SCHEDULED -> {
                state.update { it.copy(submitting = false) }
                effects.send(
                    SignInEffect.PendingDeletion(problem.deletionScheduledAt.orEmpty(), problem.restoreToken.orEmpty()),
                )
            }
            ErrorCode.INVALID_CREDENTIALS -> state.update {
                it.copy(
                    submitting = false,
                    error = error,
                    passwordError = true,
                )
            }
            ErrorCode.EMAIL_NOT_VERIFIED -> state.update {
                it.copy(
                    submitting = false,
                    error = error,
                    notVerified = true,
                )
            }
            ErrorCode.TOO_MANY_ATTEMPTS, ErrorCode.TOO_MANY_REQUESTS -> {
                state.update { it.copy(submitting = false, error = error, cooldownSeconds = COOLDOWN_SECONDS) }
                countDown()
            }
            ErrorCode.VALIDATION_FAILED -> state.update {
                it.copy(
                    submitting = false,
                    error = error,
                    emailError = "email" in problem.fieldErrors,
                    passwordError = "password" in problem.fieldErrors,
                )
            }
            else -> state.update { it.copy(submitting = false, error = error) }
        }
    }

    private suspend fun countDown() {
        while (state.value.cooldownSeconds > 0) {
            delay(ONE_SECOND_MS)
            state.update { it.copy(cooldownSeconds = it.cooldownSeconds - 1) }
        }
    }

    companion object {
        private const val COOLDOWN_SECONDS = 30
        private const val ONE_SECOND_MS = 1_000L
    }
}
