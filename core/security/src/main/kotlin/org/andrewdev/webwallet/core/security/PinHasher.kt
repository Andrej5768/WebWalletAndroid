package org.andrewdev.webwallet.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

/** A hashed PIN: the salt and the PBKDF2 output, both stored. */
data class PinHash(val salt: ByteArray, val hash: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is PinHash && salt.contentEquals(other.salt) && hash.contentEquals(other.hash)

    override fun hashCode(): Int = 31 * salt.contentHashCode() + hash.contentHashCode()
}

/**
 * PBKDF2-HMAC-SHA256 with 310 000 iterations, a 16-byte random salt and a 32-byte output (docs/03 §1); the check
 * is constant-time. Hashing takes a noticeable moment on purpose, so callers run it off the main thread.
 */
class PinHasher @Inject constructor() {

    private val random = SecureRandom()

    fun hash(pin: String): PinHash {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PinHash(salt, derive(pin, salt))
    }

    fun matches(pin: String, stored: PinHash): Boolean = MessageDigest.isEqual(derive(pin, stored.salt), stored.hash)

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 310_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256

        /** `1234`, `4321`, `1111`: too easy to guess; the user may still insist (docs/03 §6.1). */
        fun isWeak(pin: String): Boolean {
            if (pin.all { it == pin[0] }) return true
            val ascending = pin.zipWithNext().all { (a, b) -> b - a == 1 }
            val descending = pin.zipWithNext().all { (a, b) -> a - b == 1 }
            return ascending || descending
        }
    }
}
