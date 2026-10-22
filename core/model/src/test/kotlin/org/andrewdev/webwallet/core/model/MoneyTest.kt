package org.andrewdev.webwallet.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `amounts of one currency add up and keep the currency`() {
        val sum = Money.of("10.50", CurrencyCode.UAH) + Money.of("-2.25", CurrencyCode.UAH)

        assertEquals(Money.of("8.25", CurrencyCode.UAH), sum)
    }

    @Test
    fun `amounts of different currencies do not add up`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.of("1", CurrencyCode.UAH) + Money.of("1", CurrencyCode.USD)
        }
    }

    @Test
    fun `scaling rounds half up to two decimals`() {
        assertEquals(BigDecimal("1.24"), Money.of("1.235", CurrencyCode.EUR).scaled().amount)
        assertTrue(Money.zero(CurrencyCode.EUR).isZero)
        assertTrue((-Money.of("3", CurrencyCode.EUR)).isNegative)
    }

    @Test
    fun `a currency code is three capital letters`() {
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode("uah") }
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode("UAHX") }
        assertEquals("PLN", CurrencyCode("PLN").toString())
    }
}
