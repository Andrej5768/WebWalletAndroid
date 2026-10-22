package org.andrewdev.webwallet.core.network

import kotlinx.serialization.json.Json
import org.andrewdev.webwallet.core.network.generated.model.AppConfigResponse
import org.andrewdev.webwallet.core.network.generated.model.LoginResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The generated models must read what the backend sends: required fields as non-null, `[type, "null"]` fields as
 * nullable (docs/02 §4.4). Fixtures are captured from the backend.
 */
class GeneratedModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `app config deserialises from the backend fixture`() {
        val config = json.decodeFromString<AppConfigResponse>(fixture("app_config.json"))

        assertEquals(1, config.android.minSupportedVersionCode)
        assertEquals("https://play.google.com/store/apps/details?id=org.andrewdev.webwallet", config.android.storeUrl)
        assertTrue(config.features.googleSignIn)
        assertFalse(config.features.push)
    }

    @Test
    fun `nullable fields of the login response are optional`() {
        val challenge = json.decodeFromString<LoginResponse>(fixture("login_challenge.json"))
        val session = json.decodeFromString<LoginResponse>(fixture("login_session.json"))

        assertNull(challenge.accessToken)
        assertEquals("c-1", challenge.twoFactorChallenge)
        assertEquals("a-1", session.accessToken)
        assertEquals("r-1", session.refreshToken)
        assertNull(session.twoFactorChallenge)
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }.readText()
}
