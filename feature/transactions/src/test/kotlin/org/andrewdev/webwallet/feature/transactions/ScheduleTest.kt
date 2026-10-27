package org.andrewdev.webwallet.feature.transactions

import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.Frequency
import org.andrewdev.webwallet.core.model.Occurrence
import org.andrewdev.webwallet.core.model.OccurrenceStatus
import org.andrewdev.webwallet.core.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleTest {

    @Test
    fun `a monthly payment on the 31st falls on the last day of shorter months and comes back`() {
        val start = LocalDate.of(2026, 1, 31)
        val dates = Schedule.nextDates(start, Frequency.MONTHLY, 1, null, start, 4)

        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30").map(LocalDate::parse),
            dates,
        )
    }

    @Test
    fun `the next dates start from the day asked and stop at the end`() {
        val start = LocalDate.of(2026, 10, 1)
        val dates = Schedule.nextDates(
            start,
            Frequency.WEEKLY,
            2,
            LocalDate.of(2026, 11, 20),
            LocalDate.of(2026, 10, 20),
            5,
        )

        assertEquals(listOf("2026-10-29", "2026-11-12").map(LocalDate::parse), dates)
        assertEquals(LocalDate.of(2026, 12, 24), Schedule.endDateForCount(start, Frequency.WEEKLY, 2, 7))
    }

    @Test
    fun `a weekly schedule is aligned to its weekday and a yearly one to its day of the year`() {
        val monday = LocalDate.of(2026, 10, 19)

        assertEquals(
            LocalDate.of(2026, 10, 23),
            Schedule.alignedStart(monday, Frequency.WEEKLY, DayOfWeek.FRIDAY, 1, 1),
        )
        assertEquals(monday, Schedule.alignedStart(monday, Frequency.WEEKLY, DayOfWeek.MONDAY, 1, 1))
        assertEquals(
            LocalDate.of(2027, 2, 28),
            Schedule.alignedStart(monday, Frequency.YEARLY, DayOfWeek.MONDAY, 2, 29),
        )
        assertEquals(
            LocalDate.of(2026, 12, 1),
            Schedule.alignedStart(monday, Frequency.YEARLY, DayOfWeek.MONDAY, 12, 1),
        )
    }

    @Test
    fun `payments per month compare the units`() {
        assertEquals(1.0, Schedule.perMonth(Frequency.MONTHLY, 1))
        assertEquals(0.5, Schedule.perMonth(Frequency.MONTHLY, 2))
        assertEquals(365.25 / 12 / 7, Schedule.perMonth(Frequency.WEEKLY, 1), 1e-9)
        assertEquals(1.0 / 12, Schedule.perMonth(Frequency.YEARLY, 1), 1e-9)
    }

    @Test
    fun `a frequent series shows its nearest date only, overdue dates each on their own`() {
        val pocket = (1..4).map { occurrence(1, "Pocket money", LocalDate.of(2026, 10, 20 + it)) }
        val rent = occurrence(2, "Rent", LocalDate.of(2026, 10, 25))
        val overdue = occurrence(3, "Gym", LocalDate.of(2026, 10, 10), overdue = true)
        val gymLater = occurrence(3, "Gym", LocalDate.of(2026, 11, 10))

        val rows = Planned.rows(listOf(overdue, pocket[0], pocket[1], rent, pocket[2], pocket[3], gymLater))

        assertEquals(listOf("Gym", "Pocket money", "Rent", "Gym"), rows.map { it.occurrence.name })
        assertEquals(4, rows[1].repeats)
        assertEquals(LocalDate.of(2026, 10, 24), rows[1].until)
        assertEquals(1, rows[3].repeats)

        val days = Planned.byDayDescending(rows)
        assertEquals(LocalDate.of(2026, 11, 10), days.first().first)
        assertEquals(LocalDate.of(2026, 10, 10), days.last().first)
    }

    private fun occurrence(recurringId: Long, name: String, date: LocalDate, overdue: Boolean = false) = Occurrence(
        recurringId, name, date, OccurrenceStatus.PENDING, overdue, TransactionType.EXPENSE, 1, BigDecimal.TEN,
        CurrencyCode.UAH, null, null, null, null, false, false, null,
    )
}
