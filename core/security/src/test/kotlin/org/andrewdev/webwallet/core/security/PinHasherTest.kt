package org.andrewdev.webwallet.core.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PinHasherTest {

    private val hasher = PinHasher()

    @Test
    fun `the same pin matches its hash and a different one does not`() {
        val stored = hasher.hash("2580")

        assertTrue(hasher.matches("2580", stored))
        assertFalse(hasher.matches("2581", stored))
        assertEquals(16, stored.salt.size)
        assertEquals(32, stored.hash.size)
    }

    @Test
    fun `every hash has its own salt`() {
        assertNotEquals(hasher.hash("2580"), hasher.hash("2580"))
    }

    @Test
    fun `sequences and repeated digits are weak`() {
        assertTrue(PinHasher.isWeak("1234"))
        assertTrue(PinHasher.isWeak("4321"))
        assertTrue(PinHasher.isWeak("1111"))
        assertTrue(PinHasher.isWeak("56789"))
        assertFalse(PinHasher.isWeak("2580"))
        assertFalse(PinHasher.isWeak("1324"))
    }
}
