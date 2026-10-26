package org.andrewdev.webwallet.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** The same cases as the web's `arithmetic.test.ts`, so both clients agree on what an expression means. */
class ArithmeticTest {

    private fun eval(text: String): BigDecimal? = Arithmetic.evaluate(text)

    @Test
    fun `plain numbers with a comma or a point`() {
        assertEquals(BigDecimal("1200.00"), eval("1200"))
        assertEquals(BigDecimal("12.50"), eval("12,5"))
        assertEquals(BigDecimal("0.50"), eval(".5"))
        assertEquals(BigDecimal("1200.00"), eval(" 1 200 "))
    }

    @Test
    fun `the four operations with precedence and parentheses`() {
        assertEquals(BigDecimal("470.00"), eval("350+120"))
        assertEquals(BigDecimal("960.00"), eval("1200*0.8"))
        assertEquals(BigDecimal("1400.00"), eval("1000+2*200"))
        assertEquals(BigDecimal("2400.00"), eval("(1000+200)*2"))
        assertEquals(BigDecimal("33.33"), eval("100/3"))
        assertEquals(BigDecimal("600.00"), eval("1200 × 0,5"))
    }

    @Test
    fun `percent is a share of its neighbour`() {
        assertEquals(BigDecimal("1020.00"), eval("1200-15%"))
        assertEquals(BigDecimal("1380.00"), eval("1200+15%"))
        assertEquals(BigDecimal("180.00"), eval("1200*15%"))
        assertEquals(BigDecimal("0.15"), eval("15%"))
    }

    @Test
    fun `incomplete or impossible expressions have no value`() {
        assertNull(eval(""))
        assertNull(eval("12+"))
        assertNull(eval("(12"))
        assertNull(eval("abc"))
        assertNull(eval("10/0"))
    }

    @Test
    fun `negative numbers`() {
        assertEquals(BigDecimal("-120.00"), eval("-120"))
        assertEquals(BigDecimal("80.00"), eval("-20+100"))
    }

    @Test
    fun `an expression is more than a number`() {
        assertTrue(Arithmetic.isExpression("12+3"))
        assertTrue(Arithmetic.isExpression("15%"))
        assertFalse(Arithmetic.isExpression("1 200,50"))
        assertFalse(Arithmetic.isExpression("-120"))
    }
}
