package org.andrewdev.webwallet.core.common

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** The UI language; numbers and dates follow it, not the device locale, to match the web (docs/02 §8). */
enum class UiLanguage(val tag: String, val locale: Locale) {
    UK("uk", Locale.forLanguageTag("uk-UA")),
    EN("en", Locale.forLanguageTag("en-GB")),
    ;

    companion object {
        fun of(tag: String?): UiLanguage = entries.firstOrNull { it.tag == tag?.take(2)?.lowercase() } ?: UK
    }
}

/**
 * Formats amounts like the web's `formatMoney`: `1 234,50 ₴` in Ukrainian, `₴1,234.50` in English, a leading minus
 * and never a negative zero. The symbol table mirrors the narrow symbols browsers use; unknown currencies show
 * their code.
 */
class MoneyFormatter(private val language: UiLanguage) {

    private val symbols = DecimalFormatSymbols.getInstance(language.locale)

    fun format(amount: BigDecimal, currency: String, signed: Boolean = false, decimals: Int = 2): String {
        val value = amount.setScale(decimals, RoundingMode.HALF_UP)
        val number = decimalFormat(decimals, decimals).format(value.abs())
        return withSymbol(number, currency, sign(value, signed))
    }

    /** `150 тис. ₴` / `₴150K`: a short amount for chart axes and tight spaces. */
    fun formatCompact(amount: BigDecimal, currency: String): String {
        val value = amount.setScale(1, RoundingMode.HALF_UP)
        val (scaled, suffix) = compact(value.abs())
        val number = decimalFormat(0, 1).format(scaled) + suffix
        return withSymbol(number, currency, sign(value, signed = false))
    }

    /** `12,5` / `12.5`: a quantity of units with up to [maxDecimals] decimals. */
    fun formatQuantity(quantity: BigDecimal, maxDecimals: Int = 4): String =
        decimalFormat(0, maxDecimals).format(quantity.setScale(maxDecimals, RoundingMode.HALF_UP))

    /** `33,3 %` / `33.3%` of a ratio such as 0.333. */
    fun formatPercent(ratio: BigDecimal, decimals: Int = 1): String {
        val percent = ratio.movePointRight(2).setScale(decimals, RoundingMode.HALF_UP)
        val number = decimalFormat(decimals, decimals).format(percent)
        return if (language == UiLanguage.UK) "$number\u00a0%" else "$number%"
    }

    fun symbol(currency: String): String = SYMBOLS[currency] ?: currency

    private fun withSymbol(number: String, currency: String, sign: String): String {
        val symbol = symbol(currency)
        return when (language) {
            UiLanguage.UK -> "$sign$number\u00a0$symbol"
            UiLanguage.EN -> "$sign$symbol$number"
        }
    }

    private fun sign(value: BigDecimal, signed: Boolean): String = when {
        value.signum() < 0 -> "-"
        value.signum() > 0 && signed -> "+"
        else -> ""
    }

    private fun compact(value: BigDecimal): Pair<BigDecimal, String> = when {
        value >= MILLION -> value.divide(MILLION, 1, RoundingMode.HALF_UP) to suffix("\u00a0млн", "M")
        value >= THOUSAND -> value.divide(THOUSAND, 1, RoundingMode.HALF_UP) to suffix("\u00a0тис.", "K")
        else -> value to ""
    }

    private fun suffix(uk: String, en: String): String = if (language == UiLanguage.UK) uk else en

    private fun decimalFormat(minDecimals: Int, maxDecimals: Int): DecimalFormat = DecimalFormat(
        "#,##0",
        symbols,
    ).apply {
        minimumFractionDigits = minDecimals
        maximumFractionDigits = maxDecimals
        isGroupingUsed = true
        roundingMode = RoundingMode.HALF_UP
    }

    companion object {
        private val THOUSAND = BigDecimal(1_000)
        private val MILLION = BigDecimal(1_000_000)

        /** The narrow symbols of the currencies the users keep money in; the rest show their ISO code. */
        private val SYMBOLS = mapOf(
            "UAH" to "₴",
            "USD" to "$",
            "EUR" to "€",
            "GBP" to "£",
            "PLN" to "zł",
            "CZK" to "Kč",
            "JPY" to "¥",
            "CHF" to "CHF",
            "CAD" to "$",
            "AUD" to "$",
            "TRY" to "₺",
            "GEL" to "₾",
            "KZT" to "₸",
            "INR" to "₹",
            "ILS" to "₪",
            "SEK" to "kr",
            "NOK" to "kr",
            "DKK" to "kr",
            "HUF" to "Ft",
            "RON" to "lei",
            "BGN" to "лв.",
            "CNY" to "¥",
        )
    }
}
