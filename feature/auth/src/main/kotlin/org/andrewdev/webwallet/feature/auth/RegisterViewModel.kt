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
import javax.inject.Inject

/** Why a password is refused, mirrored from the backend rules (docs/03 §3.3). */
enum class PasswordProblem { TOO_SHORT, TOO_LONG }

/** The same limits as the backend: 8 characters and at most 72 UTF-8 bytes (BCrypt). */
fun passwordProblem(password: String): PasswordProblem? = when {
    password.length < PASSWORD_MIN_LENGTH -> PasswordProblem.TOO_SHORT
    password.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX_BYTES -> PasswordProblem.TOO_LONG
    else -> null
}

private const val PASSWORD_MIN_LENGTH = 8
private const val PASSWORD_MAX_BYTES = 72

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val submitting: Boolean = false,
    val error: ApiError? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    /** Set after the first submit attempt; before it, incomplete fields are not shown as wrong. */
    val validated: Boolean = false,
) {
    val emailInvalid: Boolean get() = validated && !email.contains('@')
    val firstNameMissing: Boolean get() = validated && firstName.isBlank()
    val passwordProblem: PasswordProblem? get() = if (validated || password.isNotEmpty()) {
        passwordProblem(
            password,
        )
    } else {
        null
    }
    val canSubmit: Boolean get() = !submitting
}

sealed interface RegisterEffect {
    data class Registered(val email: String) : RegisterEffect
}

@HiltViewModel
class RegisterViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

    private val state = MutableStateFlow(RegisterUiState())
    private val effects = Channel<RegisterEffect>(Channel.BUFFERED)

    val uiState: StateFlow<RegisterUiState> = state.asStateFlow()
    val effect = effects.receiveAsFlow()

    fun onEmailChange(value: String) = state.update {
        it.copy(email = value, error = null, fieldErrors = it.fieldErrors - "email")
    }

    fun onPasswordChange(value: String) = state.update {
        it.copy(password = value, error = null, fieldErrors = it.fieldErrors - "password")
    }

    fun onFirstNameChange(value: String) = state.update {
        it.copy(firstName = value, error = null, fieldErrors = it.fieldErrors - "firstName")
    }

    fun onLastNameChange(value: String) = state.update {
        it.copy(lastName = value, error = null, fieldErrors = it.fieldErrors - "lastName")
    }

    fun submit() {
        val current = state.value.copy(validated = true)
        state.value = current
        val invalid = current.emailInvalid || current.firstNameMissing || current.passwordProblem != null
        if (invalid || !current.canSubmit) return
        state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = auth.register(current.email, current.password, current.firstName, current.lastName)) {
                is ApiResult.Ok -> {
                    state.update { it.copy(submitting = false) }
                    effects.send(RegisterEffect.Registered(current.email.trim().lowercase()))
                }
                is ApiResult.Err -> state.update {
                    it.copy(
                        submitting = false,
                        error = result.error,
                        fieldErrors = (result.error as? ApiError.Problem)?.fieldErrors.orEmpty(),
                    )
                }
            }
        }
    }
}
