package org.andrewdev.webwallet.core.auth

import org.andrewdev.webwallet.core.datastore.SessionStore
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.flatMap
import org.andrewdev.webwallet.core.network.generated.api.AuthenticationApi
import org.andrewdev.webwallet.core.network.generated.api.CurrentUserApi
import org.andrewdev.webwallet.core.network.generated.api.DemoApi
import org.andrewdev.webwallet.core.network.generated.model.ClaimRequest
import org.andrewdev.webwallet.core.network.generated.model.EmailRequest
import org.andrewdev.webwallet.core.network.generated.model.LoginRequest
import org.andrewdev.webwallet.core.network.generated.model.LoginResponse
import org.andrewdev.webwallet.core.network.generated.model.RegisterRequest
import org.andrewdev.webwallet.core.network.generated.model.ResetPasswordRequest
import org.andrewdev.webwallet.core.network.generated.model.RestoreAccountRequest
import org.andrewdev.webwallet.core.network.generated.model.TokenResponse
import org.andrewdev.webwallet.core.network.generated.model.TwoFactorLoginRequest
import org.andrewdev.webwallet.core.network.generated.model.VerifyEmailRequest
import javax.inject.Inject
import javax.inject.Singleton

/** What a sign-in attempt led to (docs/03 §3.1). */
sealed interface SignInOutcome {
    data object SignedIn : SignInOutcome

    data class TwoFactorRequired(val challenge: String) : SignInOutcome
}

/** The sign-in flows: each ends in [SessionManager.signIn] once the backend hands out tokens. */
@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthenticationApi,
    private val userApi: CurrentUserApi,
    private val demoApi: DemoApi,
    private val session: SessionManager,
    private val store: SessionStore,
) {

    suspend fun signIn(email: String, password: String): ApiResult<SignInOutcome> {
        val request = LoginRequest(
            email = email.trim().lowercase(),
            password = password,
            trustedDeviceToken = store.trustedDeviceToken(),
        )
        return apiCall { api.login(request) }.flatMap { complete(it) }
    }

    /** Opens a demo sandbox with example data and signs in to it; no registration needed. */
    suspend fun signInDemo(): ApiResult<SignInOutcome> = apiCall { demoApi.startDemo() }.flatMap { complete(it) }

    /** Keeps the demo sandbox as an ordinary account; the email is verified through the usual message. */
    suspend fun claimDemo(email: String, password: String, firstName: String): ApiResult<Unit> =
        apiCall { demoApi.claimDemo(ClaimRequest(email.trim().lowercase(), password, firstName.trim())) }

    suspend fun confirmTwoFactor(challenge: String, code: String, trustDevice: Boolean): ApiResult<SignInOutcome> =
        apiCall { api.loginTwoFactor(TwoFactorLoginRequest(challenge, code.trim(), trustDevice)) }
            .flatMap { accept(it) }

    suspend fun register(email: String, password: String, firstName: String, lastName: String?): ApiResult<Unit> =
        apiCall {
            api.register(
                RegisterRequest(
                    email = email.trim().lowercase(),
                    password = password,
                    firstName = firstName.trim(),
                    lastName = lastName?.trim()?.ifEmpty { null },
                ),
            )
        }

    suspend fun resendVerification(email: String): ApiResult<Unit> =
        apiCall { api.resendVerification(EmailRequest(email.trim().lowercase())) }

    suspend fun forgotPassword(email: String): ApiResult<Unit> =
        apiCall { api.forgotPassword(EmailRequest(email.trim().lowercase())) }

    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> =
        apiCall { api.resetPassword(ResetPasswordRequest(newPassword, token)) }

    suspend fun verifyEmail(token: String): ApiResult<Unit> = apiCall { api.verifyEmail(VerifyEmailRequest(token)) }

    /** Cancels a scheduled deletion with the token of the problem or of the emailed link; signs in on success. */
    suspend fun restore(token: String): ApiResult<SignInOutcome> =
        apiCall { api.restore(RestoreAccountRequest(token)) }.flatMap { complete(it) }

    private suspend fun complete(response: LoginResponse): ApiResult<SignInOutcome> {
        response.twoFactorChallenge?.let { return ApiResult.Ok(SignInOutcome.TwoFactorRequired(it)) }
        val tokens = TokenResponse(
            accessToken = checkNotNull(response.accessToken) { "No access token" },
            expiresIn = response.expiresIn ?: DEFAULT_EXPIRES_IN,
            tokenType = response.tokenType ?: "Bearer",
            refreshToken = response.refreshToken,
            trustedDeviceToken = null,
        )
        return accept(tokens)
    }

    /** With the tokens in hand, the profile is read once to know whose session this is. */
    private suspend fun accept(tokens: TokenResponse): ApiResult<SignInOutcome> {
        session.replaceTokens(tokens)
        return apiCall { userApi.me() }.flatMap { user ->
            session.signIn(tokens, user.id, user.email, listOfNotNull(user.firstName, user.lastName).joinToString(" "))
            ApiResult.Ok(SignInOutcome.SignedIn)
        }
    }

    companion object {
        private const val DEFAULT_EXPIRES_IN = 900L
    }
}
