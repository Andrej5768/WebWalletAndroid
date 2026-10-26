package org.andrewdev.webwallet.core.model

import java.math.BigDecimal

/** The nine account types of the backend; the order is the one of the web's type picker. */
enum class AccountType {
    CARD,
    FOP,
    CREDIT_CARD,
    CASH,
    SAVINGS,
    DEPOSIT,
    LOAN,
    DEBT,
    INVESTMENT,
    ;

    /** Interest rate makes sense for these types only (web `hasInterest`). */
    val hasInterest: Boolean get() = this == DEPOSIT || this == SAVINGS || this == LOAN

    /** A limit is asked for credit cards and loans. */
    val hasCreditLimit: Boolean get() = this == CREDIT_CARD || this == LOAN

    /** Whether a new account of the type is in the budget by default: money the user spends from. */
    val inBudgetByDefault: Boolean get() = this == CARD || this == CASH || this == CREDIT_CARD || this == FOP
}

/** The groups of the accounts screen, in display order; a type belongs to exactly one group. */
enum class AccountGroup(val types: List<AccountType>) {
    CARDS(listOf(AccountType.CARD)),
    FOP(listOf(AccountType.FOP)),
    CASH(listOf(AccountType.CASH)),
    CREDIT(listOf(AccountType.CREDIT_CARD)),
    SAVINGS(listOf(AccountType.SAVINGS, AccountType.DEPOSIT)),
    DEBTS(listOf(AccountType.LOAN, AccountType.DEBT)),
    INVESTMENTS(listOf(AccountType.INVESTMENT)),
    ARCHIVED(emptyList()),
    ;

    companion object {
        fun of(type: AccountType): AccountGroup = entries.first { type in it.types }
    }
}

data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val currency: CurrencyCode,
    val balance: BigDecimal,
    /** The balance converted to the base currency; null when the backend has no rate. */
    val balanceInBase: BigDecimal?,
    val openingBalance: BigDecimal,
    val creditLimit: BigDecimal?,
    val availableCredit: BigDecimal?,
    val interestRate: BigDecimal?,
    val inBudget: Boolean,
    val archived: Boolean,
    val sortOrder: Int,
    /** How many operations touch the account, to order pickers by use. */
    val operations: Long,
) {
    val money: Money get() = Money(balance, currency)
}

/** The figures of `GET /accounts/summary`, all in the base currency. */
data class AccountSummary(
    val currency: CurrencyCode,
    val netWorth: BigDecimal,
    val available: BigDecimal,
    val availableWithCredit: BigDecimal,
    val readyToAssign: BigDecimal,
    /** Currencies the backend has no rate for, so the figures above miss them. */
    val unconvertedCurrencies: List<String>,
)

/** A person from `GET /debts`: positive balance means they owe the user. */
data class DebtPerson(
    val payeeId: Long,
    val name: String?,
    val balance: BigDecimal,
    val currency: CurrencyCode,
    val operations: Long,
    val lastDate: java.time.LocalDate?,
)
