package org.andrewdev.webwallet.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class DateFormatterTest {

    private val today = LocalDate.of(2026, 10, 26)
    private val uk = DateFormatter(UiLanguage.UK)
    private val en = DateFormatter(UiLanguage.EN)

    @Test
    fun `short dates drop the year within the current year`() {
        assertEquals("2 жовт.", uk.short(LocalDate.of(2026, 10, 2), today))
        assertEquals("2 жовт. 2025", uk.short(LocalDate.of(2025, 10, 2), today))
        assertEquals("2 Oct", en.short(LocalDate.of(2026, 10, 2), today))
        assertEquals("2 Oct 2025", en.short(LocalDate.of(2025, 10, 2), today))
    }

    @Test
    fun `day headers use the relative words and then the weekday`() {
        assertEquals("Сьогодні", uk.dayHeader(today, today, "Сьогодні", "Учора"))
        assertEquals("Учора", uk.dayHeader(today.minusDays(1), today, "Сьогодні", "Учора"))
        assertEquals("Пʼятниця, 23 жовтня", uk.dayHeader(LocalDate.of(2026, 10, 23), today, "Сьогодні", "Учора"))
        assertEquals("Friday, 23 October", en.dayHeader(LocalDate.of(2026, 10, 23), today, "Today", "Yesterday"))
        assertEquals("23 жовтня 2025", uk.dayHeader(LocalDate.of(2025, 10, 23), today, "Сьогодні", "Учора"))
    }

    @Test
    fun `months and long dates`() {
        assertEquals("Жовтень 2026", uk.month(YearMonth.of(2026, 10)))
        assertEquals("October 2026", en.month(YearMonth.of(2026, 10)))
        assertEquals("26 жовтня 2026", uk.long(today))
        assertEquals("26 October 2026", en.long(today))
    }
}
