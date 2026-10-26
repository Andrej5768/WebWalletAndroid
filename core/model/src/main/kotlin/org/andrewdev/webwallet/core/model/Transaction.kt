package org.andrewdev.webwallet.core.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

enum class TransactionType { EXPENSE, INCOME, TRANSFER }

/** Where a transaction came from; anything but MANUAL keeps amount, date and account read-only. */
enum class TransactionSource { MANUAL, ZENMONEY, MONOBANK, PRIVATBANK }

data class Split(
    val id: Long?,
    val amount: BigDecimal,
    val categoryId: Long?,
    val comment: String?,
)

/** A reference to a named thing (payee, category) carried inside another response. */
data class Named(val id: Long, val name: String)

data class Transaction(
    val id: Long,
    val type: TransactionType,
    val date: LocalDate,
    val accountId: Long,
    /** Always positive, as the backend sends it; [signedAmount] applies the sign of the type. */
    val amount: BigDecimal,
    val currency: CurrencyCode,
    val toAccountId: Long?,
    val toAmount: BigDecimal?,
    val toCurrency: CurrencyCode?,
    val categoryId: Long?,
    val payee: Named?,
    val comment: String?,
    val tagIds: List<Long>,
    val splits: List<Split>,
    val originalAmount: BigDecimal?,
    val originalCurrency: CurrencyCode?,
    val source: TransactionSource,
    /** A balance correction: counted in the account balance, left out of reports and the budget. */
    val adjustment: Boolean,
    val recurring: Named?,
    val createdAt: OffsetDateTime,
) {
    val money: Money get() = Money(amount, currency)

    /** Expenses negative, income positive, transfers as they are (web `signedAmount`). */
    val signedAmount: BigDecimal get() = if (type == TransactionType.EXPENSE) amount.negate() else amount

    val isTransfer: Boolean get() = type == TransactionType.TRANSFER

    val isImported: Boolean get() = source != TransactionSource.MANUAL

    /** Split into several categories; a single split is the plain category (web `isSplit`). */
    val isSplit: Boolean get() = splits.size > 1
}

/** One page of a paged list, as the backend pages them. */
data class Page<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page + 1 < totalPages
}
