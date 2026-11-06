package org.andrewdev.webwallet.feature.imports

import org.andrewdev.webwallet.core.data.TickerChoiceRequest
import org.andrewdev.webwallet.core.model.BrokerOperation
import org.andrewdev.webwallet.core.model.BrokerOperationStatus
import org.andrewdev.webwallet.core.model.BrokerOperationType
import org.andrewdev.webwallet.core.model.BrokerTicker
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.InstrumentSuggestion
import org.andrewdev.webwallet.core.model.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class BrokerModelTest {

    private fun row(
        key: String,
        type: BrokerOperationType?,
        status: BrokerOperationStatus = BrokerOperationStatus.NEW,
        quantity: String? = null,
        cash: String = "0",
        toAmount: String? = null,
        toCurrency: String? = null,
    ) = BrokerOperation(
        key = key,
        date = LocalDate.of(2026, 10, 1),
        type = type,
        status = status,
        skip = null,
        ticker = "AAPL",
        description = "",
        quantity = quantity?.let(::BigDecimal),
        price = null,
        amount = BigDecimal(cash).abs(),
        cash = BigDecimal(cash),
        fee = BigDecimal.ZERO,
        tax = BigDecimal.ZERO,
        currency = "USD",
        toAmount = toAmount?.let(::BigDecimal),
        toCurrency = toCurrency,
    )

    private val ticker = BrokerTicker(
        ticker = "AAPL",
        currency = "USD",
        quantityAfter = BigDecimal("10"),
        shortfall = false,
        suggestion = InstrumentSuggestion(null, "AAPL", "Apple", "XNAS", "USD", manual = false),
    )

    @Test
    fun `sorts rows into the filters`() {
        assertEquals(BrokerFilter.TRADES, BrokerModel.filterOf(row("1", BrokerOperationType.BUY)))
        assertEquals(BrokerFilter.INCOME, BrokerModel.filterOf(row("2", BrokerOperationType.COUPON)))
        assertEquals(BrokerFilter.CASH, BrokerModel.filterOf(row("3", BrokerOperationType.WITHDRAWAL)))
        assertEquals(
            BrokerFilter.SKIPPED,
            BrokerModel.filterOf(row("4", BrokerOperationType.BUY, BrokerOperationStatus.DUPLICATE)),
        )
    }

    @Test
    fun `units and cash leave out the excluded rows`() {
        val buy = row("1", BrokerOperationType.BUY, quantity = "4", cash = "-400")
        val sell = row("2", BrokerOperationType.SELL, quantity = "1", cash = "100")
        val exchange = row("3", BrokerOperationType.EXCHANGE, cash = "-50", toAmount = "2000", toCurrency = "UAH")
        val rows = listOf(buy, sell, exchange)
        assertEquals(0, BigDecimal("7").compareTo(BrokerModel.quantityAfter(ticker, rows, setOf("1", "2"))))
        val cash = BrokerModel.cashAfter(listOf(Money(BigDecimal("500"), CurrencyCode.USD)), rows, setOf("1", "3"))
        assertEquals(0, BigDecimal("950").compareTo(cash.first { it.currency == CurrencyCode.USD }.amount))
        assertEquals(0, BigDecimal("-2000").compareTo(cash.first { it.currency == CurrencyCode.UAH }.amount))
    }

    @Test
    fun `asks only for tickers created by hand against a found suggestion`() {
        assertFalse(BrokerModel.isManual(ticker, null))
        assertTrue(BrokerModel.isManual(ticker, TickerChoice.MANUAL))
        assertTrue(BrokerModel.isManual(ticker.copy(suggestion = ticker.suggestion.copy(manual = true)), null))
        assertEquals(
            listOf(TickerChoiceRequest("AAPL", manual = true)),
            BrokerModel.choiceRequest(mapOf("AAPL" to TickerChoice.MANUAL, "MSFT" to TickerChoice.SUGGESTED)),
        )
    }
}
