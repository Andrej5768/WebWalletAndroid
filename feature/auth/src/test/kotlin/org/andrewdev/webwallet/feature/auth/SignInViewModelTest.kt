package org.andrewdev.webwallet.feature.auth

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.andrewdev.webwallet.core.auth.AuthRepository
import org.andrewdev.webwallet.core.auth.SignInOutcome
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.ErrorCode
import org.andrewdev.webwallet.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class SignInViewModelTest {

    private val auth = mockk<AuthRepository>()
    private val viewModel = SignInViewModel(auth)

    private fun problem(code: ErrorCode, status: Int, extensions: Map<String, String> = emptyMap()) = ApiError.Problem(
        status,
        code,
        "",
        "detail",
        emptyMap(),
        JsonObject(extensions.mapValues { JsonPrimitive(it.value) }),
    )

    @Test
    fun `the button needs an email and a password`() {
        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.onEmailChange("ada@example.com")
        viewModel.onPasswordChange("secret")
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `a two-factor challenge is an effect and the password is cleared`() = runTest {
        coEvery { auth.signIn("ada@example.com", "secret") } returns
            ApiResult.Ok(SignInOutcome.TwoFactorRequired("c-1"))
        viewModel.onEmailChange("ada@example.com")
        viewModel.onPasswordChange("secret")

        viewModel.effect.test {
            viewModel.submit()
            assertEquals(SignInEffect.TwoFactor("c-1"), awaitItem())
        }
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun `wrong credentials mark the password field`() = runTest {
        coEvery { auth.signIn(any(), any()) } returns ApiResult.Err(problem(ErrorCode.INVALID_CREDENTIALS, 401))
        viewModel.onEmailChange("ada@example.com")
        viewModel.onPasswordChange("x")

        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.passwordError)
        assertFalse(state.submitting)
    }

    @Test
    fun `a scheduled deletion goes to its screen with the restore token`() = runTest {
        coEvery { auth.signIn(any(), any()) } returns ApiResult.Err(
            problem(
                ErrorCode.ACCOUNT_DELETION_SCHEDULED,
                403,
                mapOf("restoreToken" to "rt", "deletionScheduledAt" to "2026-11-09T10:00:00Z"),
            ),
        )
        viewModel.onEmailChange("ada@example.com")
        viewModel.onPasswordChange("x")

        viewModel.effect.test {
            viewModel.submit()
            assertEquals(SignInEffect.PendingDeletion("2026-11-09T10:00:00Z", "rt"), awaitItem())
        }
    }

    @Test
    fun `too many attempts disables the button for a while`() = runTest {
        coEvery { auth.signIn(any(), any()) } returns ApiResult.Err(problem(ErrorCode.TOO_MANY_ATTEMPTS, 429))
        viewModel.onEmailChange("ada@example.com")
        viewModel.onPasswordChange("x")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.cooldownSeconds)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `an unverified email offers to resend`() = runTest {
        coEvery { auth.signIn(any(), any()) } returns ApiResult.Err(problem(ErrorCode.EMAIL_NOT_VERIFIED, 403))
        coEvery { auth.resendVerification("ada@example.com") } returns ApiResult.Ok(Unit)
        viewModel.onEmailChange("ada@example.com")
        viewModel.onPasswordChange("x")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.notVerified)

        viewModel.effect.test {
            viewModel.resendVerification()
            assertEquals(SignInEffect.CheckMail("ada@example.com"), awaitItem())
        }
    }
}
