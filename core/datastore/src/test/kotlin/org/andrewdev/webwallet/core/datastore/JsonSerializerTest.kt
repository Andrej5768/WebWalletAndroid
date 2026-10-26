package org.andrewdev.webwallet.core.datastore

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class JsonSerializerTest {

    private val serializer = JsonSerializer(SettingsData.serializer(), SettingsData())

    @Test
    fun `settings survive a round trip`() = runTest {
        val settings = SettingsData(theme = ThemeSetting.DARK, language = LanguageSetting.EN, telemetryConsent = true)
        val out = ByteArrayOutputStream()

        serializer.writeTo(settings, out)
        val read = serializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertEquals(settings, read)
    }

    @Test
    fun `keys of a newer app are ignored and missing ones take their defaults`() = runTest {
        val read = serializer.readFrom(ByteArrayInputStream("""{"theme":"LIGHT","future":1}""".toByteArray()))

        assertEquals(SettingsData(theme = ThemeSetting.LIGHT), read)
    }

    @Test
    fun `a broken file is reported as corruption so the defaults replace it`() = runTest {
        assertThrows(CorruptionException::class.java) {
            kotlinx.coroutines.runBlocking { serializer.readFrom(ByteArrayInputStream("not json".toByteArray())) }
        }
    }
}
