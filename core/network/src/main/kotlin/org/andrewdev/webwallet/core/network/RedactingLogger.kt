package org.andrewdev.webwallet.core.network

import okhttp3.logging.HttpLoggingInterceptor

/**
 * The debug request log with secrets blanked (docs/02 §4.2, step 4): the bearer header and the token, password and
 * code fields of JSON bodies. Release builds never install it.
 */
class RedactingLogger(private val sink: (String) -> Unit) : HttpLoggingInterceptor.Logger {

    override fun log(message: String) {
        sink(redact(message))
    }

    companion object {
        private val SECRET_FIELDS = Regex(
            "\"(accessToken|refreshToken|trustedDeviceToken|password|newPassword|currentPassword|token|code|secret|" +
                "restoreToken|otpauthUri|recoveryCodes)\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|\\[[^]]*])",
        )
        private val BEARER = Regex("(Authorization: Bearer )\\S+")

        fun redact(message: String): String = message
            .replace(BEARER) { "${it.groupValues[1]}[redacted]" }
            .replace(SECRET_FIELDS) { "\"${it.groupValues[1]}\":\"[redacted]\"" }
    }
}
