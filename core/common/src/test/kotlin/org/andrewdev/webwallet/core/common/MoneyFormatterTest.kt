package org.andrewdev.webwallet.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyFormatterTest {

    private val uk = MoneyFormatter(UiLanguage.UK)
    private val en = MoneyFormatter(UiLanguage.EN)

    @Test
    fun `ukrainian amounts group with a space and put the symbol after`() {
        assertEquals("1 234,50 ₴", uk.format(BigDecimal("1234.5"), "UAH"))
        assertEquals("-52 000,00 $", uk.format(BigDecimal("-52000"), "USD"))
        assertEquals("+120,00 €", uk.format(BigDecimal("120"), "EUR", signed = true))
    }

    @Test
    fun `english amounts put the symbol before and the sign first`() {
        assertEquals("₴1,234.50", en.format(BigDecimal("1234.5"), "UAH"))
        assertEquals("-£12.00", en.format(BigDecimal("-12"), "GBP"))
    }

    @Test
    fun `a zero never has a minus and unknown currencies show the code`() {
        assertEquals("0,00 ₴", uk.format(BigDecimal("-0.001"), "UAH"))
        assertEquals("0,00 ₴", uk.format(BigDecimal("0"), "UAH", signed = true))
        assertEquals("10,00 XYZ", uk.format(BigDecimal("10"), "XYZ"))
    }

    @Test
    fun `decimals can be dropped for long-term estimates`() {
        assertEquals("1 235 ₴", uk.format(BigDecimal("1234.5"), "UAH", decimals = 0))
    }

    @Test
    fun `compact amounts for chart axes`() {
        assertEquals("150 тис. ₴", uk.formatCompact(BigDecimal("150000"), "UAH"))
        assertEquals("1,2 млн ₴", uk.formatCompact(BigDecimal("1234567"), "UAH"))
        assertEquals("₴150K", en.formatCompact(BigDecimal("150000"), "UAH"))
        assertEquals("₴950", en.formatCompact(BigDecimal("950"), "UAH"))
    }

    @Test
    fun `quantities and percents follow the language`() {
        assertEquals("12,5", uk.formatQuantity(BigDecimal("12.5")))
        assertEquals("12.5", en.formatQuantity(BigDecimal("12.5")))
        assertEquals("33,3 %", uk.formatPercent(BigDecimal("0.3333")))
        assertEquals("33.3%", en.formatPercent(BigDecimal("0.3333")))
    }

    @Test
    fun `the language is read from a locale tag with ukrainian as the default`() {
        assertEquals(UiLanguage.EN, UiLanguage.of("en-US"))
        assertEquals(UiLanguage.UK, UiLanguage.of("uk"))
        assertEquals(UiLanguage.UK, UiLanguage.of(null))
        assertEquals(UiLanguage.UK, UiLanguage.of("de"))
    }
}
