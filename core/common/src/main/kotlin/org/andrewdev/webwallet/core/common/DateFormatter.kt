package org.andrewdev.webwallet.core.common

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Dates in the UI language (docs/02 §8): `26 жовт.` within the current year, `26 жовт. 2025` otherwise; feed day
 * headers `Сьогодні`, `Учора`, then `п’ятниця, 17 жовтня`. The relative words are passed in, since they are string
 * resources of the caller.
 */
class DateFormatter(private val language: UiLanguage) {

    private val locale: Locale = language.locale
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val dayMonthLong = DateTimeFormatter.ofPattern("d MMMM", locale)
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
    private val full = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

    /** `26 жовт.` or, in another year than today, `26 жовт. 2025`. */
    fun short(date: LocalDate, today: LocalDate): String =
        if (date.year == today.year) dayMonth.format(date) else dayMonthYear.format(date)

    /** `26 жовтня 2026`: for sentences such as "deleted on …". */
    fun long(date: LocalDate): String = full.format(date)

    /** Feed day header: the relative word for today and yesterday, else the weekday with the date. */
    fun dayHeader(date: LocalDate, today: LocalDate, todayWord: String, yesterdayWord: String): String = when (date) {
        today -> todayWord
        today.minusDays(1) -> yesterdayWord
        else -> {
            val text = if (date.year == today.year) weekdayDayMonth.format(date) else full.format(date)
            text.replaceFirstChar { it.titlecase(locale) }
        }
    }

    /** `Жовтень 2026` for the month switcher. */
    fun month(month: YearMonth): String = monthYear.format(month).replaceFirstChar { it.titlecase(locale) }

    /** `17 жовтня` for the "Due 17 Oct" label of planned payments. */
    fun dayMonth(date: LocalDate): String = dayMonthLong.format(date)

    fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
}
