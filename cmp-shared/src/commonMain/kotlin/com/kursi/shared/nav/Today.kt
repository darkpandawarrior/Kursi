package com.kursi.shared.nav

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * M6d — the app's source of "today" for the Daily Challenge. The calendar epoch-day (days since
 * 1970-01-01, UTC) is read from the platform clock HERE in the UI/nav layer, never inside pure
 * :engine / daily-derivation code — so the deterministic [com.kursi.feature.game.DailyChallenge]
 * stays a pure function of the day index.
 *
 * UTC by design: the daily is meant to be the SAME challenge for everyone on a given calendar date,
 * so a single global day boundary (rather than a per-device local one) is the correct anchor.
 */
@OptIn(ExperimentalTime::class)
object Today {
    private const val MILLIS_PER_DAY = 86_400_000L

    /** The current UTC calendar epoch-day (days since the Unix epoch). */
    fun epochDay(): Long = Clock.System.now().toEpochMilliseconds() / MILLIS_PER_DAY
}
