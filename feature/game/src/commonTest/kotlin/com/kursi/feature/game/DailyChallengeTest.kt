package com.kursi.feature.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M6d — the Daily Challenge must be a DETERMINISTIC pure function of the calendar day: the same date
 * yields the same (seed, players, difficulty) on every device and every run, while different days
 * generally differ (good spread, not a constant).
 */
class DailyChallengeTest {

    @Test
    fun same_day_yields_same_challenge() {
        val day = 20_000L // an arbitrary fixed epoch-day
        val a = DailyChallenge.forDay(day)
        val b = DailyChallenge.forDay(day)
        assertEquals(a, b, "a fixed day must produce an identical challenge")
        // And specifically each field is stable.
        assertEquals(a.seed, b.seed)
        assertEquals(a.players, b.players)
        assertEquals(a.difficulty, b.difficulty)
    }

    @Test
    fun challenge_fields_are_in_valid_bands() {
        for (day in 19_000L..19_400L) {
            val ch = DailyChallenge.forDay(day)
            assertEquals(day, ch.epochDay)
            assertTrue(ch.players in 2..10, "players out of band: ${ch.players}")
            assertTrue(ch.seed >= 0, "seed must be non-negative: ${ch.seed}")
            // difficulty is a valid enum value by construction.
            assertTrue(ch.difficulty in Difficulty.entries)
        }
    }

    @Test
    fun consecutive_days_are_not_all_identical() {
        // Over a window, the seed should take many distinct values (the hash spreads the days).
        val seeds = (0L until 60L).map { DailyChallenge.forDay(it).seed }.toSet()
        assertTrue(seeds.size > 40, "expected good seed spread over 60 days, got ${seeds.size} distinct")
        // Difficulty should also vary across the window (not a constant).
        val diffs = (0L until 60L).map { DailyChallenge.forDay(it).difficulty }.toSet()
        assertTrue(diffs.size >= 2, "expected difficulty to vary across days, got $diffs")
    }
}
