package org.andrewdev.webwallet.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.andrewdev.webwallet.core.security.KeystoreCipher
import org.andrewdev.webwallet.core.security.PinHash
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** What `session.json` holds (docs/03 §1); the tokens are sealed by the Keystore key before they land here. */
@Serializable
data class SessionData(
    val refreshTokenSealed: String? = null,
    val trustedDeviceTokenSealed: String? = null,
    val userId: Long? = null,
    val email: String? = null,
    val displayName: String? = null,
    val lock: LockData = LockData(),
)

/** The app lock (docs/03 §6): the PIN hash, the biometric flag, the timeout and the failure counter. */
@Serializable
data class LockData(
    val enabled: Boolean = false,
    val pinSalt: String? = null,
    val pinHash: String? = null,
    val biometric: Boolean = false,
    val timeoutMinutes: Int = 0,
    val failedAttempts: Int = 0,
    val cooldownUntilEpochMs: Long = 0,
    /** Shown once after the first sign-in on a device. */
    val offered: Boolean = false,
    val showBalancesInWidgets: Boolean = false,
)

/** The signed-in user's tokens and the lock settings; cleared on sign-out. */
@Singleton
class SessionStore @Inject constructor(
    private val store: DataStore<SessionData>,
    private val cipher: KeystoreCipher,
) {
    val data: Flow<SessionData> = store.data

    val lock: Flow<LockData> = store.data.map { it.lock }

    suspend fun current(): SessionData = store.data.first()

    suspend fun refreshToken(): String? = current().refreshTokenSealed?.let(::open)

    suspend fun trustedDeviceToken(): String? = current().trustedDeviceTokenSealed?.let(::open)

    suspend fun saveRefreshToken(token: String) {
        store.updateData { it.copy(refreshTokenSealed = seal(token)) }
    }

    suspend fun saveTrustedDeviceToken(token: String?) {
        store.updateData { it.copy(trustedDeviceTokenSealed = token?.let(::seal)) }
    }

    suspend fun saveUser(userId: Long, email: String, displayName: String) {
        store.updateData { it.copy(userId = userId, email = email, displayName = displayName) }
    }

    suspend fun updateLock(transform: (LockData) -> LockData) {
        store.updateData { it.copy(lock = transform(it.lock)) }
    }

    suspend fun savePin(hash: PinHash) {
        updateLock {
            it.copy(
                enabled = true,
                pinSalt = encode(hash.salt),
                pinHash = encode(hash.hash),
                failedAttempts = 0,
            )
        }
    }

    suspend fun pinHash(): PinHash? {
        val lock = current().lock
        val salt = lock.pinSalt ?: return null
        val hash = lock.pinHash ?: return null
        return PinHash(decode(salt), decode(hash))
    }

    /** Everything goes, tokens and the lock included; the Keystore key is renewed so old copies are unreadable. */
    suspend fun clear() {
        store.updateData { SessionData() }
        cipher.reset()
    }

    private fun seal(text: String): String = encode(cipher.encrypt(text.encodeToByteArray()))

    private fun open(sealed: String): String? =
        runCatching { cipher.decrypt(decode(sealed)).decodeToString() }.getOrNull()

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
}
