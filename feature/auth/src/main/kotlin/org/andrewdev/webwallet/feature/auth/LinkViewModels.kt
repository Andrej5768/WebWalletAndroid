package org.andrewdev.webwallet.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.AuthRepository
import org.andrewdev.webwallet.core.auth.SignInOutcome
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.ErrorCode
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.NotificationsApi
import javax.inject.Inject

/** One request with one outcome: the screens that act on an emailed link or a single button. */
sealed interface LinkState {
    data object Idle : LinkState

    data object Working : LinkState

    data object Done : LinkState

    /** The link is used up or wrong (INVALID_TOKEN). */
    data object Expired : LinkState

    data class Failed(val error: ApiError) : LinkState
}

private fun ApiError.toLinkState(): LinkState =
    if (this is ApiError.Problem && code == ErrorCode.INVALID_TOKEN) LinkState.Expired else LinkState.Failed(this)

/** "Check your mail": resends the verification message (docs/03 §3.3). */
@HiltViewModel
class CheckMailViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val state = MutableStateFlow<LinkState>(LinkState.Idle)
    val uiState: StateFlow<LinkState> = state.asStateFlow()

    fun resend(email: String) {
        state.value = LinkState.Working
        viewModelScope.launch {
            state.value = when (val result = auth.resendVerification(email)) {
                is ApiResult.Ok -> LinkState.Done
                is ApiResult.Err -> LinkState.Failed(result.error)
            }
        }
    }
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val state = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = state.asStateFlow()

    fun onEmailChange(email: String) = state.update { it.copy(email = email, link = LinkState.Idle) }

    fun submit() {
        val email = state.value.email
        if (!email.contains('@')) return
        state.update { it.copy(link = LinkState.Working) }
        viewModelScope.launch {
            val link = when (val result = auth.forgotPassword(email)) {
                is ApiResult.Ok -> LinkState.Done
                is ApiResult.Err -> LinkState.Failed(result.error)
            }
            state.update { it.copy(link = link) }
        }
    }
}

data class ForgotPasswordUiState(val email: String = "", val link: LinkState = LinkState.Idle)

data class ResetPasswordUiState(
    val password: String = "",
    val repeat: String = "",
    val validated: Boolean = false,
    val link: LinkState = LinkState.Idle,
) {
    val passwordProblem: PasswordProblem? get() = if (validated || password.isNotEmpty()) {
        passwordProblem(
            password,
        )
    } else {
        null
    }
    val mismatch: Boolean get() = validated && repeat != password
}

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val state = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = state.asStateFlow()

    fun onPasswordChange(value: String) = state.update { it.copy(password = value) }

    fun onRepeatChange(value: String) = state.update { it.copy(repeat = value) }

    fun submit(token: String) {
        val current = state.value.copy(validated = true)
        state.value = current
        if (current.passwordProblem != null || current.mismatch) return
        state.update { it.copy(link = LinkState.Working) }
        viewModelScope.launch {
            val link = when (val result = auth.resetPassword(token, current.password)) {
                is ApiResult.Ok -> LinkState.Done
                is ApiResult.Err -> result.error.toLinkState()
            }
            state.update { it.copy(link = link) }
        }
    }
}

/** Verifies the email of the link once; the expired branch offers a resend by email (docs/03 §3.4). */
@HiltViewModel
class VerifyEmailViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val state = MutableStateFlow<LinkState>(LinkState.Idle)
    private val resendState = MutableStateFlow<LinkState>(LinkState.Idle)
    val uiState: StateFlow<LinkState> = state.asStateFlow()
    val resend: StateFlow<LinkState> = resendState.asStateFlow()

    fun verify(token: String?) {
        if (state.value != LinkState.Idle) return
        if (token == null) {
            state.value = LinkState.Expired
            return
        }
        state.value = LinkState.Working
        viewModelScope.launch {
            state.value = when (val result = auth.verifyEmail(token)) {
                is ApiResult.Ok -> LinkState.Done
                is ApiResult.Err -> result.error.toLinkState()
            }
        }
    }

    fun resend(email: String) {
        resendState.value = LinkState.Working
        viewModelScope.launch {
            resendState.value = when (val result = auth.resendVerification(email)) {
                is ApiResult.Ok -> LinkState.Done
                is ApiResult.Err -> LinkState.Failed(result.error)
            }
        }
    }
}

/** Cancels a scheduled deletion, from the sign-in problem or from the emailed link (docs/03 §3.6). */
@HiltViewModel
class RestoreViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val state = MutableStateFlow<LinkState>(LinkState.Idle)
    val uiState: StateFlow<LinkState> = state.asStateFlow()

    /** The outcome besides the state: a two-factor challenge means the restore worked and sign-in goes on. */
    private val challengeState = MutableStateFlow<String?>(null)
    val challenge: StateFlow<String?> = challengeState.asStateFlow()

    fun restore(token: String) {
        if (state.value == LinkState.Working) return
        state.value = LinkState.Working
        viewModelScope.launch {
            state.value = when (val result = auth.restore(token)) {
                is ApiResult.Ok -> {
                    (result.value as? SignInOutcome.TwoFactorRequired)?.let { challengeState.value = it.challenge }
                    LinkState.Done
                }
                is ApiResult.Err -> result.error.toLinkState()
            }
        }
    }
}

/** The public unsubscribe page of the digest; works signed out (docs/03 §7). */
@HiltViewModel
class UnsubscribeViewModel @Inject constructor(private val api: NotificationsApi) : ViewModel() {
    private val state = MutableStateFlow<LinkState>(LinkState.Idle)
    private val resubscribed = MutableStateFlow(false)
    val uiState: StateFlow<LinkState> = state.asStateFlow()
    val subscribedAgain: StateFlow<Boolean> = resubscribed.asStateFlow()

    fun unsubscribe(user: String, token: String) {
        if (state.value != LinkState.Idle) return
        val userId = user.toLongOrNull()
        if (userId == null) {
            state.value = LinkState.Expired
            return
        }
        state.value = LinkState.Working
        viewModelScope.launch {
            state.value = when (val result = apiCall { api.unsubscribe(userId, token) }) {
                is ApiResult.Ok -> LinkState.Done
                is ApiResult.Err -> result.error.toLinkState()
            }
        }
    }

    fun resubscribe(user: String, token: String) {
        val userId = user.toLongOrNull() ?: return
        viewModelScope.launch {
            if (apiCall { api.resubscribe(userId, token) } is ApiResult.Ok) resubscribed.value = true
        }
    }
}
