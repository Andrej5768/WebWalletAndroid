package org.andrewdev.webwallet.feature.lock.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Whether a strong biometric is enrolled, so the setup can offer it (docs/03 §6.1). */
fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

/** Shows the system prompt; [onFailed] gets a message when the attempt ends in an error, nothing when dismissed. */
fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    negative: String,
    onSuccess: () -> Unit,
    onFailed: (String?) -> Unit,
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val dismissed = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onFailed(if (dismissed) null else errString.toString())
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negative)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build(),
    )
}
