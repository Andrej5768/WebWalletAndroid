package org.andrewdev.webwallet.core.auth

import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.datastore.SessionStore
import org.andrewdev.webwallet.core.network.AccessTokenProvider
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.ErrorCode
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.AuthenticationApi
import org.andrewdev.webwallet.core.network.generated.model.RefreshTokenRequest
import org.andrewdev.webwallet.core.network.generated.model.TokenResponse
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Whether someone is signed in (docs/02 §6); the lock is a separate state of [AppLock]. */
sealed interface AuthState {
    /** Before the first restore attempt. */
    data object Unknown : AuthState

    /** With the reason when a session ended by itself, to explain on the sign-in screen. */
    data class SignedOut(val reason: SignOutReason? = null) : AuthState

    data class SignedIn(val userId: Long) : AuthState
}

enum class SignOutReason { SESSION_EXPIRED, USER }

/** Work to do when the session ends: clear the database, cancel jobs. Bound into a set by the modules that own data. */
fun interface SessionCleanup {
    suspend fun onSignOut()
}

/**
 * Owns the access token (memory only) and the refresh token (sealed in the session store), refreshes single-flight
 * and ends the session when the backend no longer accepts it (docs/03 §2). Also the [AccessTokenProvider] of the
 * network layer.
 */
@Singleton
class SessionManager @Inject constructor(
    private val api: Lazy<AuthenticationApi>,
    private val store: SessionStore,
    private val cleanups: Set<@JvmSuppressWildcards SessionCleanup>,
    private val clock: KyivClock,
) : AccessTokenProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val state = MutableStateFlow<AuthState>(AuthState.Unknown)

    @Volatile
    private var access: AccessToken? = null

    val authState: StateFlow<AuthState> = state.asStateFlow()

    override fun accessToken(): String? = access?.value

    /** Whether the access token ends within a minute, so a proactive refresh is due (docs/03 §2). */
    fun accessTokenEndsSoon(): Boolean = access?.let {
        it.expiresAt.isBefore(clock.now().plusSeconds(SOON_SECONDS))
    } ?: true

    /** App start: a stored refresh token is tried; without one the user is signed out. */
    suspend fun restore() {
        val userId = store.current().userId
        if (store.refreshToken() == null || userId == null) {
            state.value = AuthState.SignedOut()
            return
        }
        when (val result = refresh()) {
            is ApiResult.Ok -> state.value = AuthState.SignedIn(userId)
            is ApiResult.Err -> if (result.error.isRejection()) {
                endSession(SignOutReason.SESSION_EXPIRED)
            } else {
                // Offline: keep the session; the first call will refresh again
                state.value = AuthState.SignedIn(userId)
            }
        }
    }

    /** A completed sign-in: the tokens of the login, two-factor or restore response. */
    suspend fun signIn(tokens: TokenResponse, userId: Long, email: String, displayName: String) {
        store.saveUser(userId, email, displayName)
        accept(tokens)
        state.value = AuthState.SignedIn(userId)
    }

    /** A password change returns new tokens; the session goes on with them (docs/03 §2). */
    suspend fun replaceTokens(tokens: TokenResponse) = accept(tokens)

    override suspend fun refreshAfterRejection(rejected: String?): String? = mutex.withLock {
        val current = access?.value
        // Another call already refreshed while this one waited
        if (current != null && current != rejected) return current
        when (val result = refreshLocked()) {
            is ApiResult.Ok -> result.value.accessToken
            is ApiResult.Err -> {
                if (result.error.isRejection()) endSession(SignOutReason.SESSION_EXPIRED)
                null
            }
        }
    }

    suspend fun refresh(): ApiResult<TokenResponse> = mutex.withLock { refreshLocked() }

    /** Ends the session on the server (best effort, 5 s) and on the device. */
    suspend fun signOut() {
        val refreshToken = store.refreshToken()
        if (refreshToken != null) {
            withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { apiCall { api.get().logout(RefreshTokenRequest(refreshToken)) } }
        }
        endSession(SignOutReason.USER)
    }

    private suspend fun refreshLocked(): ApiResult<TokenResponse> {
        val refreshToken = store.refreshToken()
            ?: return ApiResult.Err(ApiError.Unexpected(null, IllegalStateException("No refresh token")))
        val result = apiCall { api.get().refresh(RefreshTokenRequest(refreshToken)) }
        if (result is ApiResult.Ok) accept(result.value)
        return result
    }

    private suspend fun accept(tokens: TokenResponse) {
        access = AccessToken(tokens.accessToken, clock.now().plusSeconds(tokens.expiresIn))
        tokens.refreshToken?.let { store.saveRefreshToken(it) }
        tokens.trustedDeviceToken?.let { store.saveTrustedDeviceToken(it) }
    }

    private suspend fun endSession(reason: SignOutReason) {
        access = null
        cleanups.forEach { cleanup ->
            runCatching { cleanup.onSignOut() }.onFailure { Timber.w(it, "Sign-out cleanup failed") }
        }
        store.clear()
        state.value = AuthState.SignedOut(reason)
    }

    private fun ApiError.isRejection(): Boolean = this is ApiError.Problem &&
        (code == ErrorCode.INVALID_REFRESH_TOKEN || code == ErrorCode.UNAUTHORIZED || status == HTTP_UNAUTHORIZED)

    /** Keeps the scope referenced for callers that fire-and-forget a proactive refresh. */
    fun refreshInBackground() {
        scope.launch { refresh() }
    }

    private class AccessToken(val value: String, val expiresAt: Instant)

    companion object {
        private const val SOON_SECONDS = 60L
        private const val LOGOUT_TIMEOUT_MS = 5_000L
        private const val HTTP_UNAUTHORIZED = 401
    }
}
