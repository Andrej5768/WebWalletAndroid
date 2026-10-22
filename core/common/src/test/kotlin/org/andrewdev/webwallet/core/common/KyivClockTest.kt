package org.andrewdev.webwallet.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class KyivClockTest {

    @Test
    fun `today is the Kyiv date, not the UTC one`() {
        // 23:30 UTC on 31 October is already 1 November in Kyiv (UTC+2)
        val clock = KyivClock.fixed(Instant.parse("2026-10-31T23:30:00Z"))

        assertEquals(LocalDate.of(2026, 11, 1), clock.today())
        assertEquals(YearMonth.of(2026, 11), clock.thisMonth())
    }

    @Test
    fun `summer time is honoured`() {
        // 21:30 UTC in July is 00:30 the next day in Kyiv (UTC+3)
        val clock = KyivClock.fixed(Instant.parse("2026-07-15T21:30:00Z"))

        assertEquals(LocalDate.of(2026, 7, 16), clock.today())
    }
}
