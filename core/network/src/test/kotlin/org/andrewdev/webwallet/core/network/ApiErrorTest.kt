package org.andrewdev.webwallet.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ApiErrorTest {

    @Test
    fun `a problem body yields its code, detail and field errors`() {
        val body = """
            {"type":"about:blank","title":"Bad Request","status":400,"detail":"Перевірте поля",
             "code":"VALIDATION_FAILED","errors":[{"field":"amount","message":"Вкажіть суму"}]}
        """.trimIndent()

        val error = ApiError.parse(400, body) as ApiError.Problem

        assertEquals(ErrorCode.VALIDATION_FAILED, error.code)
        assertEquals("Перевірте поля", error.detail)
        assertEquals(mapOf("amount" to "Вкажіть суму"), error.fieldErrors)
        assertNull(error.restoreToken)
    }

    @Test
    fun `the extensions of a scheduled deletion are readable`() {
        val body = """{"title":"Forbidden","status":403,"code":"ACCOUNT_DELETION_SCHEDULED",
            "restoreToken":"rt-1","deletionScheduledAt":"2026-11-09T10:00:00Z"}"""

        val error = ApiError.parse(403, body) as ApiError.Problem

        assertEquals("rt-1", error.restoreToken)
        assertEquals("2026-11-09T10:00:00Z", error.deletionScheduledAt)
    }

    @Test
    fun `an unknown code and a retry-after header are kept`() {
        val error = ApiError.parse(429, """{"title":"x","status":429,"code":"NEWER_CODE"}""", "30") as ApiError.Problem

        assertEquals(ErrorCode.UNKNOWN, error.code)
        assertEquals(30, error.retryAfterSeconds)
    }

    @Test
    fun `a body that is not a problem is unexpected`() {
        assertInstanceOf(ApiError.Unexpected::class.java, ApiError.parse(502, "<html>Bad gateway</html>"))
        assertInstanceOf(ApiError.Unexpected::class.java, ApiError.parse(500, null))
    }

    @Test
    fun `the debug log blanks tokens and passwords`() {
        val redacted = RedactingLogger.redact(
            "Authorization: Bearer abc.def " +
                """{"email":"a@b.c","password":"s3cret","refreshToken":"r-1","recoveryCodes":["a","b"]}""",
        )

        assertEquals(
            "Authorization: Bearer [redacted] " +
                """{"email":"a@b.c","password":"[redacted]",""" +
                """"refreshToken":"[redacted]","recoveryCodes":"[redacted]"}""",
            redacted,
        )
    }
}
