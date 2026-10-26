package org.andrewdev.webwallet.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts the stored secrets (refresh token, trusted-device token) with an AES-256-GCM key that never leaves the
 * Android Keystore (docs/03 §1). The key needs no user authentication, so background work can refresh the session,
 * but it is usable only while the device is unlocked. StrongBox when the device has it, the TEE otherwise.
 */
@Singleton
class KeystoreCipher @Inject constructor() {

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    /** The IV followed by the ciphertext; the IV is random per call, as GCM requires. */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.size > IV_BYTES) { "Sealed data is too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    /** Forgets the key, so that whatever was encrypted with it is unreadable: part of a sign-out. */
    fun reset() {
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return generate(strongBox = true) ?: checkNotNull(generate(strongBox = false)) { "Cannot create the key" }
    }

    private fun generate(strongBox: Boolean): SecretKey? {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setUserAuthenticationRequired(false)
            .setUnlockedDeviceRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
                init(spec)
                generateKey()
            }
        } catch (e: StrongBoxUnavailableException) {
            Timber.d(e, "No StrongBox, using the TEE")
            null
        }
    }

    companion object {
        const val ALIAS = "webwallet.session"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
