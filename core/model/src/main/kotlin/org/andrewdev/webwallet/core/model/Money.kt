package org.andrewdev.webwallet.core.model

import java.math.BigDecimal
import java.math.RoundingMode

/** An ISO 4217 code such as `UAH`; the backend stores currencies as these three letters. */
@JvmInline
value class CurrencyCode(val code: String) {
    init {
        require(code.length == CODE_LENGTH && code.all { it in 'A'..'Z' }) { "Not a currency code: $code" }
    }

    override fun toString(): String = code

    companion object {
        private const val CODE_LENGTH = 3
        val UAH: CurrencyCode = CurrencyCode("UAH")
        val USD: CurrencyCode = CurrencyCode("USD")
        val EUR: CurrencyCode = CurrencyCode("EUR")
    }
}

/** An amount that always carries its currency; never a Double. */
data class Money(val amount: BigDecimal, val currency: CurrencyCode) {

    val isNegative: Boolean get() = amount.signum() < 0

    val isZero: Boolean get() = amount.signum() == 0

    /** The amount with the two decimals money has, rounded half up like the backend. */
    fun scaled(): Money = copy(amount = amount.setScale(SCALE, RoundingMode.HALF_UP))

    operator fun plus(other: Money): Money {
        require(other.currency == currency) { "Cannot add ${other.currency} to $currency" }
        return copy(amount = amount + other.amount)
    }

    operator fun unaryMinus(): Money = copy(amount = amount.negate())

    companion object {
        const val SCALE = 2

        fun of(amount: String, currency: CurrencyCode): Money = Money(BigDecimal(amount), currency)

        fun zero(currency: CurrencyCode): Money = Money(BigDecimal.ZERO.setScale(SCALE), currency)
    }
}
