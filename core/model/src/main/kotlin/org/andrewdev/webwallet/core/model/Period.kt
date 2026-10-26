package org.andrewdev.webwallet.core.model

import java.time.LocalDate

/** The period presets of the web (`src/lib/periods.ts`), in the order of the chip row. */
enum class PeriodPreset { MONTH, PREV_MONTH, QUARTER, YEAR, TWELVE_MONTHS, ALL, CUSTOM }

/** An inclusive date range; a null end is unbounded. */
data class Period(val from: LocalDate?, val to: LocalDate?) : java.io.Serializable {

    operator fun contains(date: LocalDate): Boolean =
        (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))

    companion object {
        val ALL = Period(null, null)

        /** The range of a preset relative to today; CUSTOM keeps the given range. */
        fun of(preset: PeriodPreset, today: LocalDate, custom: Period = ALL): Period = when (preset) {
            PeriodPreset.MONTH -> Period(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
            PeriodPreset.PREV_MONTH -> {
                val previous = today.minusMonths(1)
                Period(previous.withDayOfMonth(1), previous.withDayOfMonth(previous.lengthOfMonth()))
            }
            PeriodPreset.QUARTER -> Period(today.minusMonths(THREE_MONTHS).plusDays(1), today)
            PeriodPreset.YEAR -> Period(today.withDayOfYear(1), today)
            PeriodPreset.TWELVE_MONTHS -> Period(today.minusMonths(ELEVEN_MONTHS).withDayOfMonth(1), today)
            PeriodPreset.ALL -> ALL
            PeriodPreset.CUSTOM -> custom
        }

        private const val THREE_MONTHS = 3L
        private const val ELEVEN_MONTHS = 11L
    }
}

/** How the hero figure of the overview is read; shared with the widgets. */
enum class BalanceMode { NET_WORTH, AVAILABLE_WITH_CREDIT, READY_TO_ASSIGN }
