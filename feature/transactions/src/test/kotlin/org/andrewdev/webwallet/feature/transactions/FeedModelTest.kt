package org.andrewdev.webwallet.feature.transactions

import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.Period
import org.andrewdev.webwallet.core.model.PeriodPreset
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.model.TransactionSource
import org.andrewdev.webwallet.core.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

class FeedModelTest {

    private fun transaction(
        id: Long,
        type: TransactionType,
        amount: String,
        date: String,
        currency: String = "UAH",
        toAccountId: Long? = null,
        adjustment: Boolean = false,
    ) =
        Transaction(
            id = id, type = type,
            date = LocalDate.parse(
                date,
            ),
            accountId = 1, amount = BigDecimal(amount), currency = CurrencyCode(currency),
            toAccountId = toAccountId, toAmount = null, toCurrency = null, categoryId = null,
            payee = null, comment = null, tagIds = emptyList(),
            splits = emptyList(), originalAmount = null, originalCurrency = null,
            source = TransactionSource.MANUAL, adjustment = adjustment,
            recurring = null, createdAt = OffsetDateTime.parse("2026-10-26T10:00:00Z"),
        )

    @Test
    fun `days keep their order and total the signed amounts per currency, transfers and corrections aside`() {
        val days = FeedViewModel.groupByDay(
            listOf(
                transaction(1, TransactionType.EXPENSE, "100", "2026-10-26"),
                transaction(2, TransactionType.INCOME, "250", "2026-10-26"),
                transaction(3, TransactionType.EXPENSE, "10", "2026-10-26", currency = "USD"),
                transaction(4, TransactionType.TRANSFER, "999", "2026-10-26", toAccountId = 2),
                transaction(5, TransactionType.EXPENSE, "40", "2026-10-26", adjustment = true),
                transaction(6, TransactionType.EXPENSE, "5", "2026-10-25"),
            ),
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 25)), days.map { it.date })
        assertEquals(listOf("UAH" to BigDecimal("150"), "USD" to BigDecimal("-10")), days[0].totals)
        assertEquals(5, days[0].transactions.size)
    }

    @Test
    fun `a transfer with the debt account on one side is a debt`() {
        val debts = setOf(9L)
        assertEquals(
            DebtDirection.GAVE,
            debtDirection(transaction(1, TransactionType.TRANSFER, "1", "2026-10-26", toAccountId = 9), debts),
        )
        assertEquals(
            DebtDirection.RECEIVED,
            debtDirection(
                transaction(1, TransactionType.TRANSFER, "1", "2026-10-26", toAccountId = 1).copy(accountId = 9),
                debts,
            ),
        )
        assertNull(debtDirection(transaction(1, TransactionType.TRANSFER, "1", "2026-10-26", toAccountId = 2), debts))
        assertNull(debtDirection(transaction(1, TransactionType.EXPENSE, "1", "2026-10-26"), debts))
    }

    @Test
    fun `the filter key names the period and every filter, and counts the active ones`() {
        val filters = FeedFilters(
            preset = PeriodPreset.CUSTOM,
            custom = Period(LocalDate.of(2026, 1, 1), null),
            accountId = 3,
            type = TransactionType.INCOME,
        )
        val filter = filters.toFilter(LocalDate.of(2026, 10, 26), "  кава ")

        assertEquals("2026-01-01|null|3|null|null|null|INCOME|кава", filter.key)
        assertEquals(2, filters.activeCount)
        assertEquals(0, FeedFilters().activeCount)
    }
}
