package org.andrewdev.webwallet.core.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.datastore.LockData
import org.andrewdev.webwallet.core.datastore.SessionStore
import org.andrewdev.webwallet.core.security.PinHasher
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The result of a PIN attempt (docs/03 §6.2). */
sealed interface UnlockResult {
    data object Unlocked : UnlockResult

    /** Wrong PIN; [attemptsLeft] before the cooldown, or [cooldownSeconds] when it already applies. */
    data class Wrong(val attemptsLeft: Int, val cooldownSeconds: Long) : UnlockResult

    /** Ten failures: the session is ended by the caller. */
    data object SignOut : UnlockResult
}

/**
 * The PIN/biometric lock over the signed-in app (docs/03 §6): locked on start and when the app was in the background
 * longer than the timeout; five wrong PINs start a 30 s cooldown, ten end the session.
 */
@Singleton
class AppLock @Inject constructor(
    private val store: SessionStore,
    private val hasher: PinHasher,
    private val clock: KyivClock,
) {
    private val lockedState = MutableStateFlow(true)
    private var backgroundedAt: Instant? = null

    /** True while the lock screen must cover the content; always false when the lock is not enabled. */
    val locked: StateFlow<Boolean> = lockedState.asStateFlow()

    val settings: Flow<LockData> = store.lock

    suspend fun isEnabled(): Boolean = store.current().lock.enabled

    /** Process start: locked when enabled. */
    suspend fun onStart() {
        lockedState.value = isEnabled()
    }

    fun onBackground() {
        backgroundedAt = clock.now()
    }

    suspend fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val lock = store.current().lock
        if (!lock.enabled) return
        val away = Duration.between(since, clock.now())
        if (away >= Duration.ofMinutes(lock.timeoutMinutes.toLong())) lockedState.value = true
    }

    /** "Lock now" in the More tab. */
    suspend fun lockNow() {
        if (isEnabled()) lockedState.value = true
    }

    @Suppress("ReturnCount")
    suspend fun unlockWithPin(pin: String): UnlockResult {
        val lock = store.current().lock
        val now = clock.now().toEpochMilli()
        if (lock.cooldownUntilEpochMs > now) {
            return UnlockResult.Wrong(0, (lock.cooldownUntilEpochMs - now + MS_PER_SECOND - 1) / MS_PER_SECOND)
        }
        val stored = store.pinHash() ?: return UnlockResult.SignOut
        val matches = withContext(Dispatchers.Default) { hasher.matches(pin, stored) }
        if (matches) {
            store.updateLock { it.copy(failedAttempts = 0, cooldownUntilEpochMs = 0) }
            lockedState.value = false
            return UnlockResult.Unlocked
        }
        val failures = lock.failedAttempts + 1
        if (failures >= SIGN_OUT_AFTER) return UnlockResult.SignOut
        val cooldown = if (failures % COOLDOWN_AFTER == 0) now + COOLDOWN_MS else 0
        store.updateLock { it.copy(failedAttempts = failures, cooldownUntilEpochMs = cooldown) }
        val left = COOLDOWN_AFTER - failures % COOLDOWN_AFTER
        return UnlockResult.Wrong(if (cooldown > 0) 0 else left, if (cooldown > 0) COOLDOWN_MS / MS_PER_SECOND else 0)
    }

    /** After a successful biometric prompt. */
    suspend fun unlockWithBiometric() {
        store.updateLock { it.copy(failedAttempts = 0, cooldownUntilEpochMs = 0) }
        lockedState.value = false
    }

    suspend fun verifyPin(pin: String): Boolean {
        val stored = store.pinHash() ?: return false
        return withContext(Dispatchers.Default) { hasher.matches(pin, stored) }
    }

    suspend fun enable(pin: String, biometric: Boolean, timeoutMinutes: Int) {
        val hash = withContext(Dispatchers.Default) { hasher.hash(pin) }
        store.savePin(hash)
        store.updateLock { it.copy(biometric = biometric, timeoutMinutes = timeoutMinutes, offered = true) }
        lockedState.value = false
    }

    suspend fun changePin(pin: String) {
        val hash = withContext(Dispatchers.Default) { hasher.hash(pin) }
        store.savePin(hash)
    }

    suspend fun setBiometric(enabled: Boolean) = store.updateLock { it.copy(biometric = enabled) }

    suspend fun setTimeout(minutes: Int) = store.updateLock { it.copy(timeoutMinutes = minutes) }

    suspend fun setShowBalancesInWidgets(show: Boolean) = store.updateLock { it.copy(showBalancesInWidgets = show) }

    suspend fun disable() {
        store.updateLock { LockData(offered = true) }
        lockedState.value = false
    }

    suspend fun markOffered() = store.updateLock { it.copy(offered = true) }

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
        val TIMEOUT_CHOICES_MINUTES = listOf(0, 1, 5, 15)
        private const val COOLDOWN_AFTER = 5
        private const val SIGN_OUT_AFTER = 10
        private const val COOLDOWN_MS = 30_000L
        private const val MS_PER_SECOND = 1_000L
    }
}
