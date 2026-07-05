package com.kursi.core.prefs

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M6d — ranked ELO standing + daily-challenge standing persistence. The store tracks rating, peak,
 * game count, and a capped rating history; the daily store advances a streak across consecutive
 * winning days, breaks it on a loss or a gap, and records one attempt per calendar day.
 */
class RankedDailyStoreTest {
    // ── Ranked ELO standing ──────────────────────────────────────────────────

    @Test
    fun freshRanked_isSeeded() {
        val prefs = AppPrefs(MapSettings())
        val r = prefs.readRankedStanding()
        assertEquals(AppPrefs.ELO_SEED, r.rating)
        assertEquals(AppPrefs.ELO_SEED, r.peak)
        assertEquals(0, r.games)
        assertTrue(r.isProvisional)
        assertEquals(listOf(AppPrefs.ELO_SEED), r.history)
    }

    @Test
    fun recordRankedResult_tracksPeakAndGamesAndHistory() {
        val prefs = AppPrefs(MapSettings())
        prefs.recordRankedResult(1040)
        prefs.recordRankedResult(1010) // dipped — peak should stay at 1040
        val r = prefs.recordRankedResult(1075)
        assertEquals(1075, r.rating)
        assertEquals(1075, r.peak)
        assertEquals(3, r.games)
        assertFalse(r.isProvisional)
        // History: baseline seed + the three recorded points.
        assertEquals(listOf(AppPrefs.ELO_SEED, 1040, 1010, 1075), r.history)
    }

    @Test
    fun rankedStanding_survivesFreshPrefsOverSameStore() {
        val store = MapSettings()
        AppPrefs(store).recordRankedResult(1234)
        val reread = AppPrefs(store).readRankedStanding()
        assertEquals(1234, reread.rating)
        assertEquals(1, reread.games)
    }

    @Test
    fun ratingHistory_isCappedToWindow() {
        val prefs = AppPrefs(MapSettings())
        for (i in 1..(AppPrefs.MAX_RATING_HISTORY + 20)) prefs.recordRankedResult(1000 + i)
        val r = prefs.readRankedStanding()
        assertEquals(AppPrefs.MAX_RATING_HISTORY, r.history.size)
        // The most-recent point is retained (last write wins).
        assertEquals(1000 + (AppPrefs.MAX_RATING_HISTORY + 20), r.history.last())
    }

    @Test
    fun sarkariRank_climbsWithRating() {
        assertEquals(SarkariRank.CLERK, SarkariRank.of(800))
        assertEquals(SarkariRank.CABINET_SECRETARY, SarkariRank.of(2000))
        // Monotonic: a higher rating never maps to a lower-ordinal rank.
        var prevOrdinal = -1
        for (rating in 0..2200 step 25) {
            val ord = SarkariRank.of(rating).ordinal
            assertTrue(ord >= prevOrdinal, "rank regressed at rating=$rating")
            prevOrdinal = ord
        }
    }

    // ── Daily challenge standing ─────────────────────────────────────────────

    @Test
    fun freshDaily_isEmpty() {
        val prefs = AppPrefs(MapSettings())
        val d = prefs.readDailyStanding()
        assertEquals(-1L, d.lastDay)
        assertEquals(0, d.streak)
        assertFalse(d.hasPlayed)
    }

    @Test
    fun consecutiveWins_buildStreak() {
        val prefs = AppPrefs(MapSettings())
        prefs.recordDailyResult(epochDay = 100, won = true) // streak 1
        prefs.recordDailyResult(epochDay = 101, won = true) // streak 2
        val d = prefs.recordDailyResult(epochDay = 102, won = true) // streak 3
        assertEquals(3, d.streak)
        assertEquals(3, d.bestStreak)
        assertEquals(3, d.won)
        assertEquals(3, d.played)
    }

    @Test
    fun lossBreaksStreak_butKeepsBest() {
        val prefs = AppPrefs(MapSettings())
        prefs.recordDailyResult(epochDay = 100, won = true)
        prefs.recordDailyResult(epochDay = 101, won = true)
        val d = prefs.recordDailyResult(epochDay = 102, won = false)
        assertEquals(0, d.streak)
        assertEquals(2, d.bestStreak)
    }

    @Test
    fun gapResetsStreakToOne() {
        val prefs = AppPrefs(MapSettings())
        prefs.recordDailyResult(epochDay = 100, won = true) // streak 1
        // Skipped day 101 — a win on 102 restarts the streak at 1, not continues to 2.
        val d = prefs.recordDailyResult(epochDay = 102, won = true)
        assertEquals(1, d.streak)
    }

    @Test
    fun sameDayIsRecordedOnce() {
        val prefs = AppPrefs(MapSettings())
        prefs.recordDailyResult(epochDay = 100, won = true)
        assertTrue(prefs.isDailyDone(100))
        // A repeat attempt for the same day is a no-op (won't double-count or alter the streak).
        val d = prefs.recordDailyResult(epochDay = 100, won = false)
        assertEquals(1, d.played)
        assertEquals(1, d.streak)
        assertTrue(d.isDoneFor(100))
        assertFalse(d.isDoneFor(101))
    }

    @Test
    fun resetLedger_wipesRankedAndDaily() {
        val store = MapSettings()
        val prefs = AppPrefs(store)
        prefs.recordRankedResult(1300)
        prefs.recordDailyResult(epochDay = 100, won = true)
        prefs.resetLedger()
        assertEquals(AppPrefs.ELO_SEED, prefs.readRankedStanding().rating)
        assertEquals(0, prefs.readRankedStanding().games)
        assertEquals(-1L, prefs.readDailyStanding().lastDay)
        assertEquals(0, prefs.readDailyStanding().streak)
    }
}
