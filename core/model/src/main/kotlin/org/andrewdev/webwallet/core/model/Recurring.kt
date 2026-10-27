package org.andrewdev.webwallet.core.model

import java.math.BigDecimal
import java.time.LocalDate

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

enum class OccurrenceStatus { PENDING, PAID, SKIPPED }

/** A recurring payment (docs/04 §6.4): a transaction template with a schedule. */
data class Recurring(
    val id: Long,
    val name: String,
    val type: TransactionType,
    val accountId: Long,
    val amount: BigDecimal,
    val currency: CurrencyCode,
    val toAccountId: Long?,
    val toAmount: BigDecimal?,
    val toCurrency: CurrencyCode?,
    val categoryId: Long?,
    val payee: Named?,
    val comment: String?,
    val frequency: Frequency,
    val interval: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    /** Create the transaction on the due date; otherwise wait for a bank match or the user. */
    val autoCreate: Boolean,
    val variableAmount: Boolean,
    /** How much a matching transaction may differ, in percent. */
    val tolerance: Int,
    /** The earliest date neither paid nor skipped; null when the schedule has ended. */
    val nextDate: LocalDate?,
) {
    val stopped: Boolean get() = nextDate == null
}

/** One scheduled date of a recurring payment, with the planned values. */
data class Occurrence(
    val recurringId: Long,
    val name: String,
    val date: LocalDate,
    val status: OccurrenceStatus,
    val overdue: Boolean,
    val type: TransactionType,
    val accountId: Long,
    val amount: BigDecimal,
    val currency: CurrencyCode,
    val toAccountId: Long?,
    val toAmount: BigDecimal?,
    val categoryId: Long?,
    val payee: Named?,
    val autoCreate: Boolean,
    val variableAmount: Boolean,
    val transactionId: Long?,
) {
    /** Expenses negative, income positive, transfers as they are. */
    val signedAmount: BigDecimal get() = if (type == TransactionType.EXPENSE) amount.negate() else amount
}

/** What the editor sends; `PUT` replaces every field. */
data class RecurringDraft(
    val name: String,
    val type: TransactionType,
    val accountId: Long,
    val amount: BigDecimal,
    val toAccountId: Long? = null,
    val toAmount: BigDecimal? = null,
    val categoryId: Long? = null,
    val payeeId: Long? = null,
    val payeeName: String? = null,
    val comment: String? = null,
    val frequency: Frequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val autoCreate: Boolean = false,
    val variableAmount: Boolean = false,
    val tolerance: Int = 10,
)
