package org.andrewdev.webwallet.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** The presets must give the same ranges as the web's `periodOf`. */
class PeriodTest {

    private val today = LocalDate.of(2026, 10, 26)

    @Test
    fun `this month spans the calendar month`() {
        assertEquals(
            Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
            Period.of(PeriodPreset.MONTH, today),
        )
    }

    @Test
    fun `the previous month spans the calendar month before`() {
        assertEquals(
            Period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
            Period.of(PeriodPreset.PREV_MONTH, today),
        )
    }

    @Test
    fun `three months end today and start the day after the same date three months ago`() {
        assertEquals(Period(LocalDate.of(2026, 7, 27), today), Period.of(PeriodPreset.QUARTER, today))
    }

    @Test
    fun `the year starts on the first of January`() {
        assertEquals(Period(LocalDate.of(2026, 1, 1), today), Period.of(PeriodPreset.YEAR, today))
    }

    @Test
    fun `twelve months start at the first day of the month eleven months ago`() {
        assertEquals(Period(LocalDate.of(2025, 11, 1), today), Period.of(PeriodPreset.TWELVE_MONTHS, today))
    }

    @Test
    fun `all is unbounded and custom keeps the given range`() {
        assertEquals(Period.ALL, Period.of(PeriodPreset.ALL, today))
        val custom = Period(LocalDate.of(2026, 1, 5), null)
        assertEquals(custom, Period.of(PeriodPreset.CUSTOM, today, custom))
    }

    @Test
    fun `contains is inclusive on both ends`() {
        val period = Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertTrue(LocalDate.of(2026, 10, 1) in period)
        assertTrue(LocalDate.of(2026, 10, 31) in period)
        assertFalse(LocalDate.of(2026, 11, 1) in period)
        assertTrue(LocalDate.of(1999, 1, 1) in Period.ALL)
    }
}
