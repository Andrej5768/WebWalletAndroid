package org.andrewdev.webwallet.feature.imports

import org.andrewdev.webwallet.core.data.TickerChoiceRequest
import org.andrewdev.webwallet.core.model.BrokerOperation
import org.andrewdev.webwallet.core.model.BrokerOperationStatus
import org.andrewdev.webwallet.core.model.BrokerOperationType
import org.andrewdev.webwallet.core.model.BrokerTicker
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/** The filter chips of the broker preview; rows that are not imported are together under SKIPPED. */
enum class BrokerFilter { ALL, TRADES, INCOME, FEES, CASH, SKIPPED }

/** What a ticker becomes: the suggested instrument or one created by hand. */
enum class TickerChoice { SUGGESTED, MANUAL }

/** The logic of the broker preview (web `src/imports/broker.ts`). */
object BrokerModel {
    private const val QUANTITY_SCALE = 8
    private const val CASH_SCALE = 4

    fun filterOf(operation: BrokerOperation): BrokerFilter {
        if (operation.status != BrokerOperationStatus.NEW) return BrokerFilter.SKIPPED
        return when (operation.type) {
            BrokerOperationType.DIVIDEND, BrokerOperationType.COUPON -> BrokerFilter.INCOME
            BrokerOperationType.FEE -> BrokerFilter.FEES
            BrokerOperationType.DEPOSIT, BrokerOperationType.WITHDRAWAL -> BrokerFilter.CASH
            else -> BrokerFilter.TRADES
        }
    }

    fun matches(operation: BrokerOperation, filter: BrokerFilter): Boolean =
        filter == BrokerFilter.ALL || filterOf(operation) == filter

    /** Created by hand: chosen so, or suggested so when nothing else was chosen. */
    fun isManual(ticker: BrokerTicker, choice: TickerChoice?): Boolean =
        choice == TickerChoice.MANUAL || (choice != TickerChoice.MANUAL && ticker.suggestion.manual)

    /** The instruments of the confirmation: only those that differ from the suggestion. */
    fun choiceRequest(choices: Map<String, TickerChoice>): List<TickerChoiceRequest> =
        choices.filterValues { it == TickerChoice.MANUAL }.keys.map { TickerChoiceRequest(it, manual = true) }

    /** Units of the ticker after the import without the excluded rows. */
    fun quantityAfter(ticker: BrokerTicker, operations: List<BrokerOperation>, excluded: Set<String>): BigDecimal {
        var quantity = ticker.quantityAfter
        operations
            .filter { it.ticker == ticker.ticker && it.status == BrokerOperationStatus.NEW && it.key in excluded }
            .forEach { operation ->
                val units = operation.quantity ?: BigDecimal.ZERO
                when (operation.type) {
                    BrokerOperationType.BUY -> quantity -= units
                    BrokerOperationType.SELL -> quantity += units
                    else -> Unit
                }
            }
        return quantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
    }

    /** Money per currency after the import without the excluded rows. */
    fun cashAfter(cash: List<Money>, operations: List<BrokerOperation>, excluded: Set<String>): List<Money> {
        val amounts = LinkedHashMap<String, BigDecimal>()
        cash.forEach { amounts[it.currency.code] = it.amount }
        operations.filter { it.status == BrokerOperationStatus.NEW && it.key in excluded }.forEach { operation ->
            amounts[operation.currency] = (amounts[operation.currency] ?: BigDecimal.ZERO) - operation.cash
            val toCurrency = operation.toCurrency
            val toAmount = operation.toAmount
            if (toCurrency != null && toAmount != null) {
                amounts[toCurrency] = (amounts[toCurrency] ?: BigDecimal.ZERO) - toAmount
            }
        }
        return amounts.map { (currency, amount) ->
            Money(amount.setScale(CASH_SCALE, RoundingMode.HALF_UP), CurrencyCode(currency))
        }
    }
}
