package org.andrewdev.webwallet.core.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The single source of "today". Dates are computed in Kyiv time whatever the device zone, as the web does, so
 * that a transaction entered late at night lands on the same day the backend and the web client show.
 */
class KyivClock(private val clock: Clock = Clock.system(ZONE)) {

    fun now(): Instant = clock.instant()

    fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), ZONE)

    fun thisMonth(): YearMonth = YearMonth.from(today())

    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Kyiv")

        /** A clock stopped at the instant, for tests and previews. */
        fun fixed(instant: Instant): KyivClock = KyivClock(Clock.fixed(instant, ZONE))
    }
}
