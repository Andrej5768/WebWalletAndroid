package org.andrewdev.webwallet.core.model

import java.math.BigDecimal
import java.time.LocalDate

/** `GET /reports/spending`: expenses by parent category with their subcategories, shares of the total. */
data class SpendingReport(
    val currency: CurrencyCode,
    val from: LocalDate,
    val to: LocalDate,
    val total: BigDecimal,
    val uncategorized: BigDecimal,
    val categories: List<CategorySpending>,
    val unconvertedCurrencies: List<String>,
)

data class CategorySpending(
    val categoryId: Long,
    val name: String,
    val amount: BigDecimal,
    /** Of the total, 0..1. */
    val share: BigDecimal,
    val children: List<CategorySpending>,
)

/** `GET /reports/income-expense`: the months of the period with their totals. */
data class IncomeExpenseReport(
    val currency: CurrencyCode,
    val income: BigDecimal,
    val expense: BigDecimal,
    val net: BigDecimal,
    val savingsRate: BigDecimal?,
    val months: List<MonthFigures>,
    val unconvertedCurrencies: List<String>,
)

data class MonthFigures(
    val month: String,
    val income: BigDecimal,
    val expense: BigDecimal,
    val net: BigDecimal,
    val savingsRate: BigDecimal?,
)

/** `GET /reports/net-worth`: assets, liabilities and net worth at the end of every month. */
data class NetWorthReport(
    val currency: CurrencyCode,
    val months: List<MonthNetWorth>,
    val unconvertedCurrencies: List<String>,
)

data class MonthNetWorth(
    val month: String,
    val assets: BigDecimal,
    val liabilities: BigDecimal,
    val netWorth: BigDecimal,
)

/** `GET /reports/payees`: the payees ranked by spending. */
data class PayeesReport(
    val currency: CurrencyCode,
    val payees: List<PayeeSpending>,
    val unconvertedCurrencies: List<String>,
)

data class PayeeSpending(val payeeId: Long, val name: String, val amount: BigDecimal, val transactions: Int)

/** `GET /analytics/compare`: two periods against each other, by day and by category. */
data class CompareReport(
    val currency: CurrencyCode,
    val fromA: LocalDate,
    val toA: LocalDate,
    val fromB: LocalDate,
    val toB: LocalDate,
    val totalA: BigDecimal,
    val totalB: BigDecimal,
    val change: BigDecimal,
    val changeShare: BigDecimal?,
    /** Cumulative spending by the day of the period; null once a period is over. */
    val days: List<CompareDay>,
    val categories: List<CategoryChange>,
    val unconvertedCurrencies: List<String>,
)

data class CompareDay(val day: Int, val a: BigDecimal?, val b: BigDecimal?)

data class CategoryChange(
    val categoryId: Long?,
    val name: String?,
    val a: BigDecimal,
    val b: BigDecimal,
    val change: BigDecimal,
    val changeShare: BigDecimal?,
)

/** `GET /analytics/trends`: spending per category per month, with the months a category ran far above its average. */
data class TrendsReport(
    val currency: CurrencyCode,
    val from: String,
    val to: String,
    val months: List<String>,
    val average: BigDecimal,
    val categories: List<CategoryTrend>,
    val unconvertedCurrencies: List<String>,
)

data class CategoryTrend(
    val categoryId: Long?,
    val name: String?,
    val amounts: List<BigDecimal>,
    val total: BigDecimal,
    val average: BigDecimal,
    val anomalies: List<Anomaly>,
)

data class Anomaly(val month: String, val amount: BigDecimal, val average: BigDecimal, val ratio: BigDecimal)

/** `GET /analytics/savings`: what was left per month against a target rate, and where it went. */
data class SavingsReport(
    val currency: CurrencyCode,
    val target: BigDecimal,
    val averageRate: BigDecimal?,
    val totalSaved: BigDecimal,
    val bestMonth: String?,
    val months: List<MonthSavings>,
    val unconvertedCurrencies: List<String>,
)

data class MonthSavings(
    val month: String,
    val income: BigDecimal,
    val expense: BigDecimal,
    val net: BigDecimal,
    val savingsRate: BigDecimal?,
    val cumulative: BigDecimal,
    val complete: Boolean,
    val destinations: List<SavingsDestination>,
)

data class SavingsDestination(val accountId: Long, val name: String, val amount: BigDecimal)

/** `GET /analytics/needs-wants`: the 50/30/20 split of a month. */
data class NeedsWantsReport(
    val currency: CurrencyCode,
    val income: BigDecimal,
    val needs: NeedsWantsPart,
    val wants: NeedsWantsPart,
    val savings: NeedsWantsPart,
    val unclassified: NeedsWantsPart,
)

data class NeedsWantsPart(val amount: BigDecimal, val share: BigDecimal?, val target: BigDecimal?)
