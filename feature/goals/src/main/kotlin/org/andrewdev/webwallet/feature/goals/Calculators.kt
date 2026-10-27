package org.andrewdev.webwallet.feature.goals

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** The web's `goals/fire.ts`, ported 1:1: FIRE, FYM and the compound interest calculator (docs/04 §10.2). */
object Calculators {

    /** Years the FIRE projection looks ahead at most. */
    private const val MAX_YEARS = 60

    /** Coast FIRE: the capital that reaches the FIRE number in this many years without contributions. */
    const val COAST_YEARS = 20

    private const val MIN_HORIZON = 20
    private const val PAST_FIRE_YEARS = 3
    private const val LEAN_SHARE = 0.7
    private const val FAT_SHARE = 1.5
    private const val BARISTA_SHARE = 0.5
    private const val MONTHS_PER_YEAR = 12
    private const val MONTHS_PER_QUARTER = 3
    private const val QUARTERS_PER_YEAR = 4

    data class FireInput(
        /** Spending of a year. */
        val expenses: Double,
        /** Safe withdrawal rate, e.g. 0.04. */
        val withdrawalRate: Double,
        /** Expected yearly return after inflation, e.g. 0.05. */
        val realReturn: Double,
        /** Saved per year, added at the end of each year. */
        val yearlySavings: Double,
        /** Capital today. */
        val capital: Double,
    )

    data class FireLevels(
        val lean: Double,
        val regular: Double,
        val fat: Double,
        val coast: Double,
        val barista: Double,
    )

    data class FireResult(
        /** The capital whose withdrawals cover the expenses. */
        val target: Double,
        /** Whole years until the capital reaches the target; 0 when it already has; null beyond 60 years. */
        val years: Int?,
        /** Capital at the end of each year from now (index 0 = today), at least 20 years and 3 past FIRE. */
        val path: List<Double>,
        val levels: FireLevels,
    )

    /** FIRE number and the years to it: yearly compounding with the savings added once a year. */
    fun fire(input: FireInput): FireResult {
        val target = if (input.withdrawalRate > 0) input.expenses / input.withdrawalRate else Double.POSITIVE_INFINITY
        val path = mutableListOf(input.capital)
        var capital = input.capital
        var years: Int? = if (capital >= target) 0 else null
        var year = 1
        while (years == null && year <= MAX_YEARS) {
            capital = capital * (1 + input.realReturn) + input.yearlySavings
            path += capital
            if (capital >= target) years = year
            year++
        }
        val horizon = max(MIN_HORIZON, (years ?: MAX_YEARS) + PAST_FIRE_YEARS)
        while (path.size <= horizon) {
            capital = capital * (1 + input.realReturn) + input.yearlySavings
            path += capital
        }
        return FireResult(
            target = target,
            years = years,
            path = path.take(horizon + 1),
            levels = FireLevels(
                lean = target * LEAN_SHARE,
                regular = target,
                fat = target * FAT_SHARE,
                coast = target / (1 + input.realReturn).pow(COAST_YEARS),
                // Half of the expenses come from the capital, the other half from a part-time job
                barista = target * BARISTA_SHARE,
            ),
        )
    }

    data class FymResult(
        /** Months the liquid money lasts at the monthly expenses; null without expenses. */
        val months: Double?,
        /** What the target months need. */
        val need: Double,
        /** What is still missing for the target, 0 once reached. */
        val left: Double,
        /** Months of savings that bring the rest; null when nothing is saved. */
        val eta: Double?,
    )

    /** "Freedom money": how long the liquid money lasts without income. */
    fun fym(liquid: Double, monthlyExpenses: Double, targetMonths: Int, monthlySavings: Double): FymResult {
        val need = targetMonths * monthlyExpenses
        val left = max(0.0, need - liquid)
        return FymResult(
            months = if (monthlyExpenses > 0) liquid / monthlyExpenses else null,
            need = need,
            left = left,
            eta = when {
                left == 0.0 -> 0.0
                monthlySavings > 0 -> left / monthlySavings
                else -> null
            },
        )
    }

    /** Milestones of the FYM track, in months. */
    val FYM_MILESTONES = listOf(1, 3, 6, 12, 24)

    /** Where the months sit on the track of the milestones, 0..1, evenly spaced between them. */
    fun trackPosition(months: Double): Double {
        if (months <= 0 || months <= FYM_MILESTONES.first()) return 0.0
        for (i in 1 until FYM_MILESTONES.size) {
            val high = FYM_MILESTONES[i]
            val low = FYM_MILESTONES[i - 1]
            if (months <= high) return (i - 1 + (months - low) / (high - low)) / (FYM_MILESTONES.size - 1)
        }
        return 1.0
    }

    enum class Capitalization { MONTHLY, QUARTERLY, YEARLY }

    data class CompoundInput(
        val initial: Double,
        /** Paid at the start of every month. */
        val monthly: Double,
        /** Yearly rate, e.g. 0.12. */
        val rate: Double,
        val years: Int,
        val capitalization: Capitalization,
        /** Yearly growth of the monthly contribution, e.g. 0.05. */
        val growth: Double,
        /** Yearly inflation, e.g. 0.08, for the values in today's prices. */
        val inflation: Double,
        /** Show the values in today's prices. */
        val real: Boolean,
    )

    data class CompoundYear(val year: Int, val balance: Double, val contributed: Double)

    data class CompoundResult(
        val rows: List<CompoundYear>,
        val balance: Double,
        val contributed: Double,
        val interest: Double,
        /** The final balance in today's prices. */
        val realBalance: Double,
    )

    /**
     * Compound interest month by month: the contribution comes at the start of a month, interest is added at the end
     * of each capitalization period, and the contribution grows once a year.
     */
    fun compound(input: CompoundInput): CompoundResult {
        var balance = input.initial
        var contributed = input.initial
        var contribution = input.monthly
        val rows = mutableListOf<CompoundYear>()
        val months = max(0, input.years) * MONTHS_PER_YEAR
        for (month in 1..months) {
            balance += contribution
            contributed += contribution
            when (input.capitalization) {
                Capitalization.MONTHLY -> balance *= 1 + input.rate / MONTHS_PER_YEAR
                Capitalization.QUARTERLY -> if (month % MONTHS_PER_QUARTER == 0) {
                    balance *= 1 + input.rate / QUARTERS_PER_YEAR
                }
                Capitalization.YEARLY -> if (month % MONTHS_PER_YEAR == 0) balance *= 1 + input.rate
            }
            if (month % MONTHS_PER_YEAR == 0) {
                val year = month / MONTHS_PER_YEAR
                val deflator = if (input.real) (1 + input.inflation).pow(year) else 1.0
                rows += CompoundYear(year, balance / deflator, contributed / deflator)
                contribution *= 1 + input.growth
            }
        }
        val last = rows.lastOrNull()
        val years = months.toDouble() / MONTHS_PER_YEAR
        return CompoundResult(
            rows = rows,
            balance = last?.balance ?: input.initial,
            contributed = last?.contributed ?: input.initial,
            interest = (last?.balance ?: input.initial) - (last?.contributed ?: input.initial),
            realBalance = balance / (1 + input.inflation).pow(years),
        )
    }

    /** Averages over the complete months with data (web `averagesOf`). */
    data class Averages(
        /** Complete months the averages are taken over. */
        val months: Int,
        val monthlyExpenses: Double?,
        val monthlySavings: Double?,
        /** Savings over income of those months, null without income. */
        val savingsRate: Double?,
    ) {
        companion object {
            val NONE = Averages(0, null, null, null)
        }
    }

    data class MonthFigures(val month: String, val income: Double, val expense: Double)

    /**
     * From the first month with any income or expense up to the month before the current one, which is not over
     * yet: empty months before the history starts would make the expenses look smaller and the savings rate higher.
     */
    fun averagesOf(months: List<MonthFigures>, currentMonth: String): Averages {
        val complete = months.filter { it.month < currentMonth }
        val first = complete.indexOfFirst { it.income != 0.0 || it.expense != 0.0 }
        val counted = if (first < 0) emptyList() else complete.drop(first)
        if (counted.isEmpty()) return Averages.NONE
        val income = counted.sumOf { it.income }
        val expense = counted.sumOf { it.expense }
        return Averages(
            months = counted.size,
            monthlyExpenses = expense / counted.size,
            monthlySavings = (income - expense) / counted.size,
            savingsRate = if (income > 0) (income - expense) / income else null,
        )
    }

    fun Double.roundedToInt(): Int = roundToInt()
}
