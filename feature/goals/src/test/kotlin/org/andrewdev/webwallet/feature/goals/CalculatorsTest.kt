package org.andrewdev.webwallet.feature.goals

import org.andrewdev.webwallet.feature.goals.Calculators.Capitalization
import org.andrewdev.webwallet.feature.goals.Calculators.CompoundInput
import org.andrewdev.webwallet.feature.goals.Calculators.FireInput
import org.andrewdev.webwallet.feature.goals.Calculators.MonthFigures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.pow

/** The fixtures of the web's `fire.test.ts`. */
class CalculatorsTest {

    @Test
    fun `finds the FIRE number and the years to it`() {
        val plain = Calculators.fire(FireInput(40000.0, 0.04, 0.0, 100000.0, 0.0))
        assertEquals(1_000_000.0, plain.target)
        assertEquals(10, plain.years)
        assertEquals(21, plain.path.size)

        val growing = Calculators.fire(FireInput(40000.0, 0.04, 0.1, 100000.0, 0.0))
        assertEquals(8, growing.years)
        assertEquals(100000.0, growing.path[1], 1e-6)
        assertEquals(210000.0, growing.path[2], 1e-6)
        assertEquals(700000.0, growing.levels.lean, 1e-6)
        assertEquals(1_500_000.0, growing.levels.fat, 1e-6)
        assertEquals(1_000_000.0 / 1.1.pow(20), growing.levels.coast, 1e-6)
        assertEquals(500_000.0, growing.levels.barista, 1e-6)

        assertEquals(0, Calculators.fire(FireInput(40000.0, 0.04, 0.0, 0.0, 2_000_000.0)).years)
        val never = Calculators.fire(FireInput(40000.0, 0.04, 0.0, 100.0, 0.0))
        assertNull(never.years)
        assertEquals(64, never.path.size)
    }

    @Test
    fun `counts the months the liquid money lasts`() {
        assertEquals(Calculators.FymResult(6.0, 120000.0, 60000.0, 12.0), Calculators.fym(60000.0, 10000.0, 12, 5000.0))
        assertEquals(Calculators.FymResult(15.0, 120000.0, 0.0, 0.0), Calculators.fym(150000.0, 10000.0, 12, 0.0))
        assertNull(Calculators.fym(60000.0, 10000.0, 12, 0.0).eta)
        assertNull(Calculators.fym(60000.0, 0.0, 12, 0.0).months)
    }

    @Test
    fun `places the months evenly between the milestones`() {
        assertEquals(0.0, Calculators.trackPosition(0.5))
        assertEquals(0.25, Calculators.trackPosition(3.0))
        assertEquals(0.5, Calculators.trackPosition(6.0))
        assertEquals(0.625, Calculators.trackPosition(9.0))
        assertEquals(1.0, Calculators.trackPosition(30.0))
    }

    @Test
    fun `compounds monthly, quarterly or yearly with growing contributions`() {
        fun base(capitalization: Capitalization) = CompoundInput(1000.0, 0.0, 0.12, 1, capitalization, 0.0, 0.0, false)
        assertEquals(1000 * 1.01.pow(12), Calculators.compound(base(Capitalization.MONTHLY)).balance, 1e-6)
        assertEquals(1000 * 1.03.pow(4), Calculators.compound(base(Capitalization.QUARTERLY)).balance, 1e-6)
        assertEquals(1120.0, Calculators.compound(base(Capitalization.YEARLY)).balance, 1e-6)

        val growing = Calculators.compound(CompoundInput(0.0, 100.0, 0.0, 2, Capitalization.MONTHLY, 0.1, 0.1, false))
        assertEquals(listOf(1200.0, 2520.0), growing.rows.map { it.balance })
        assertEquals(2520.0, growing.contributed, 1e-9)
        assertEquals(0.0, growing.interest, 1e-9)
        assertEquals(2520 / 1.21, growing.realBalance, 1e-6)

        val real = Calculators.compound(CompoundInput(0.0, 100.0, 0.0, 1, Capitalization.MONTHLY, 0.0, 0.1, true))
        assertEquals(1200 / 1.1, real.rows[0].balance, 1e-6)
    }

    @Test
    fun `averages skip the empty months before the history and the current month`() {
        val months = listOf(
            MonthFigures("2026-06", 0.0, 0.0),
            MonthFigures("2026-07", 1000.0, 600.0),
            MonthFigures("2026-08", 1000.0, 800.0),
            MonthFigures("2026-09", 0.0, 0.0),
            MonthFigures("2026-10", 500.0, 100.0),
        )

        val averages = Calculators.averagesOf(months, "2026-10")

        assertEquals(3, averages.months)
        assertEquals(1400.0 / 3, averages.monthlyExpenses!!, 1e-9)
        assertEquals(600.0 / 3, averages.monthlySavings!!, 1e-9)
        assertEquals(0.3, averages.savingsRate!!, 1e-9)
        assertEquals(Calculators.Averages.NONE, Calculators.averagesOf(months.take(1), "2026-10"))
    }
}
