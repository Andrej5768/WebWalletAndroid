package org.andrewdev.webwallet.core.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Arithmetic in an amount field, the same rules as the web's `arithmetic.ts`: `1200*0.8`, `350+120`, `1200-15%`.
 * Percent is relative to what it is applied to: `a + b%` and `a - b%` add or take a share of a, `a * b%` is a share
 * of a, a lone `15%` is 0.15. Spaces are ignored and a comma is a decimal point, as in the Ukrainian locale.
 */
object Arithmetic {

    private val operators = Regex("[-+*/%()×x÷]")

    /** Whether the text is more than a plain number: it has an operator, a percent or parentheses. */
    fun isExpression(text: String): Boolean = operators.containsMatchIn(normalize(text).removePrefix("-"))

    /** The value of the text rounded to the cents, or null when it is empty or not a complete expression. */
    @Suppress("ReturnCount")
    fun evaluate(text: String): BigDecimal? {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return null
        val value = Parser(normalized).parse() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return BigDecimal(value).setScale(2, RoundingMode.HALF_UP)
    }

    private fun normalize(text: String): String = text
        .replace(Regex("\\s"), "")
        .replace(',', '.')
        .replace('×', '*')
        .replace('x', '*')
        .replace('÷', '/')

    private class Part(val value: Double, val percent: Boolean) {
        val plain: Double get() = if (percent) value / PERCENT else value
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun parse(): Double? {
            val result = expression() ?: return null
            if (pos != text.length) return null
            return result.plain
        }

        private fun expression(): Part? {
            var left = term() ?: return null
            while (peek() == '+' || peek() == '-') {
                val sign = if (next() == '+') 1.0 else -1.0
                val right = term() ?: return null
                val base = left.plain
                left = Part(
                    value = if (right.percent) base * (1 + sign * right.value / PERCENT) else base + sign * right.value,
                    percent = false,
                )
            }
            return left
        }

        @Suppress("ReturnCount")
        private fun term(): Part? {
            var left = factor() ?: return null
            while (peek() == '*' || peek() == '/') {
                val multiply = next() == '*'
                val right = factor() ?: return null
                val other = right.plain
                if (!multiply && other == 0.0) return null
                left = Part(value = if (multiply) left.plain * other else left.plain / other, percent = false)
            }
            return left
        }

        @Suppress("ReturnCount")
        private fun factor(): Part? {
            var sign = 1.0
            while (peek() == '-' || peek() == '+') {
                if (next() == '-') sign = -sign
            }
            var part: Part?
            if (peek() == '(') {
                next()
                part = expression() ?: return null
                if (next() != ')') return null
                if (part.percent) part = Part(part.plain, percent = false)
            } else {
                part = number() ?: return null
            }
            if (peek() == '%') {
                next()
                part = Part(part.value, percent = true)
            }
            return Part(sign * part.value, part.percent)
        }

        private fun number(): Part? {
            val match = numberPattern.find(text.substring(pos)) ?: return null
            pos += match.value.length
            return Part(match.value.toDouble(), percent = false)
        }

        private fun peek(): Char? = text.getOrNull(pos)

        private fun next(): Char? = text.getOrNull(pos++)

        companion object {
            private val numberPattern = Regex("^\\d+(?:\\.\\d+)?|^\\.\\d+")
        }
    }

    private const val PERCENT = 100.0
}
