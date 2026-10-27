package org.andrewdev.webwallet.feature.transactions

import org.andrewdev.webwallet.core.model.Frequency
import org.andrewdev.webwallet.core.model.Occurrence
import java.time.DayOfWeek
import java.time.LocalDate

/** The web's `recurring/schedule.ts`: dates of a schedule, counted from its start like the backend does. */
object Schedule {

    /** Payments per month of each unit, to compare weekly or yearly payments with monthly ones. */
    private const val DAYS_PER_MONTH = 365.25 / 12
    private const val DAYS_PER_WEEK = 7
    private const val MONTHS_PER_YEAR = 12
    private const val MAX_STEPS = 5000

    /** A monthly payment on the 31st falls on the last day of shorter months and comes back after them. */
    fun occurrenceDate(start: LocalDate, frequency: Frequency, interval: Int, index: Int): LocalDate {
        val steps = (index * interval).toLong()
        return when (frequency) {
            Frequency.DAILY -> start.plusDays(steps)
            Frequency.WEEKLY -> start.plusWeeks(steps)
            Frequency.MONTHLY -> start.plusMonths(steps)
            Frequency.YEARLY -> start.plusYears(steps)
        }
    }

    /** The next scheduled dates from the day on (inclusive), at most [count], none past the end. */
    fun nextDates(
        start: LocalDate,
        frequency: Frequency,
        interval: Int,
        endDate: LocalDate?,
        from: LocalDate,
        count: Int,
    ): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var index = 0
        while (dates.size < count && index < MAX_STEPS) {
            val date = occurrenceDate(start, frequency, interval, index)
            if (endDate != null && date.isAfter(endDate)) break
            if (!date.isBefore(from)) dates += date
            index++
        }
        return dates
    }

    /** The date of the last of [count] payments, for an end given as a number of payments. */
    fun endDateForCount(start: LocalDate, frequency: Frequency, interval: Int, count: Int): LocalDate =
        occurrenceDate(start, frequency, interval, maxOf(0, count - 1))

    /**
     * The first date on or after [from] that falls on the weekday of a weekly schedule, or on the day and month of a
     * yearly one (the 29th of February falls on the 28th in other years, as the backend does). Other schedules start
     * on [from].
     */
    fun alignedStart(from: LocalDate, frequency: Frequency, weekday: DayOfWeek, month: Int, day: Int): LocalDate =
        when (frequency) {
            Frequency.WEEKLY -> {
                val ahead = (weekday.value - from.dayOfWeek.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
                from.plusDays(ahead.toLong())
            }
            Frequency.YEARLY -> {
                val thisYear = inYear(from.year, month, day)
                if (thisYear.isBefore(from)) inYear(from.year + 1, month, day) else thisYear
            }
            Frequency.DAILY, Frequency.MONTHLY -> from
        }

    /** How many payments happen in a month on average. */
    fun perMonth(frequency: Frequency, interval: Int): Double = when (frequency) {
        Frequency.DAILY -> DAYS_PER_MONTH
        Frequency.WEEKLY -> DAYS_PER_MONTH / DAYS_PER_WEEK
        Frequency.MONTHLY -> 1.0
        Frequency.YEARLY -> 1.0 / MONTHS_PER_YEAR
    } / interval

    private fun inYear(year: Int, month: Int, day: Int): LocalDate {
        val first = LocalDate.of(year, month, 1)
        return first.withDayOfMonth(minOf(day, first.lengthOfMonth()))
    }
}

/** A planned payment as the feed shows it; a frequent series stands for its later dates too (web `planned.ts`). */
data class PlannedRow(
    val occurrence: Occurrence,
    /** How many dates of the series this row stands for (1 for a single date). */
    val repeats: Int,
    /** The last of those dates. */
    val until: LocalDate,
)

object Planned {
    /** Series with more upcoming dates than this show only the nearest one. */
    private const val MAX_DATES_OF_SERIES = 2

    /**
     * Overdue dates each on their own; of a series with more than two upcoming dates (weekly pocket money, a daily
     * subscription) only the nearest one, which says how many follow; other upcoming dates one by one.
     */
    fun rows(occurrences: List<Occurrence>): List<PlannedRow> {
        val upcoming = occurrences.filter { !it.overdue }.groupBy { it.recurringId }
        return occurrences.mapNotNull { occurrence ->
            val series = upcoming[occurrence.recurringId].orEmpty()
            when {
                occurrence.overdue || series.size <= MAX_DATES_OF_SERIES -> PlannedRow(occurrence, 1, occurrence.date)
                series.first() == occurrence -> PlannedRow(occurrence, series.size, series.last().date)
                else -> null
            }
        }
    }

    /** Rows by day, the latest day first like the feed. */
    fun byDayDescending(rows: List<PlannedRow>): List<Pair<LocalDate, List<PlannedRow>>> =
        rows.sortedByDescending { it.occurrence.date }.groupBy { it.occurrence.date }.map { it.key to it.value }
}
