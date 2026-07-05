package com.kursi.core.prefs

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M6b — lifetime decision-quality ledger: folds per-game tallies into persisted counters, derives the
 * accuracy / EV-lost / challenge / bluff readouts + the in-voice grade, survives a fresh AppPrefs over
 * the same store, and is wiped by a career reset.
 */
class DecisionLedgerTest {
    @Test
    fun freshDecisionLedger_isEmptyAndZero() {
        val prefs = AppPrefs(MapSettings())
        val dl = prefs.readDecisionLedger()
        assertTrue(dl.isEmpty)
        assertEquals(0, dl.decisions)
        assertEquals(0, dl.accuracyPct)
        assertEquals(0, dl.avgEvLostPct)
        assertEquals(0, dl.challengeAccuracyPct)
        assertEquals(0, dl.bluffSuccessPct)
        assertEquals(DecisionGrade.UNRATED, dl.grade)
    }

    @Test
    fun recordDecisionTally_accumulatesAndDerivesReadouts() {
        val prefs = AppPrefs(MapSettings())

        // Game 1: 8 decisions, 6 matched best, 240 milli EV lost total (3% avg).
        prefs.recordDecisionTally(
            DecisionTally(
                decisions = 8,
                matchedBest = 6,
                evLostMilli = 240L,
                challenges = 2,
                challengesGood = 1,
                bluffsTried = 3,
                bluffsOk = 2,
            ),
        )
        // Game 2: 12 decisions, 9 matched best, 360 milli EV lost.
        val after =
            prefs.recordDecisionTally(
                DecisionTally(
                    decisions = 12,
                    matchedBest = 9,
                    evLostMilli = 360L,
                    challenges = 2,
                    challengesGood = 2,
                    bluffsTried = 1,
                    bluffsOk = 1,
                ),
            )

        assertEquals(20, after.decisions)
        assertEquals(15, after.matchedBest)
        assertEquals(600L, after.evLostMilli)
        assertEquals(75, after.accuracyPct) // 15 / 20
        // avg EV lost = (600/1000)/20 = 0.03 → 3%
        assertEquals(3, after.avgEvLostPct)
        // challenges: 4 total, 3 good → 75%
        assertEquals(75, after.challengeAccuracyPct)
        // bluffs: 4 tried, 3 ok → 75%
        assertEquals(75, after.bluffSuccessPct)
        assertFalse(after.isEmpty)
    }

    @Test
    fun grade_tiersOnAccuracyAndEvBled() {
        // SHARP: ≥70% match AND ≤5% bled, sample ≥6.
        assertEquals(DecisionGrade.SHARP, DecisionGrade.of(accuracyPct = 72, avgEvLostPct = 4, decisions = 30))
        // RECKLESS: <45% match.
        assertEquals(DecisionGrade.RECKLESS, DecisionGrade.of(accuracyPct = 40, avgEvLostPct = 4, decisions = 30))
        // RECKLESS: heavy EV bleed even with decent match.
        assertEquals(DecisionGrade.RECKLESS, DecisionGrade.of(accuracyPct = 80, avgEvLostPct = 14, decisions = 30))
        // STEADY: middle band.
        assertEquals(DecisionGrade.STEADY, DecisionGrade.of(accuracyPct = 55, avgEvLostPct = 8, decisions = 30))
        // UNRATED: too few decisions to rate.
        assertEquals(DecisionGrade.UNRATED, DecisionGrade.of(accuracyPct = 90, avgEvLostPct = 0, decisions = 4))
    }

    @Test
    fun decisionLedger_persistsAcrossAppPrefsInstances() {
        val backing = MapSettings()
        AppPrefs(backing).recordDecisionTally(
            DecisionTally(decisions = 5, matchedBest = 4, evLostMilli = 50L, challenges = 1, challengesGood = 1),
        )
        val reread = AppPrefs(backing).readDecisionLedger()
        assertEquals(5, reread.decisions)
        assertEquals(4, reread.matchedBest)
        assertEquals(50L, reread.evLostMilli)
        assertEquals(1, reread.challenges)
    }

    @Test
    fun resetLedger_alsoWipesDecisionLedger() {
        val prefs = AppPrefs(MapSettings())
        prefs.recordGame(true, 1, 1, listOf("dalla_tiwari"))
        prefs.recordDecisionTally(DecisionTally(decisions = 10, matchedBest = 7, evLostMilli = 100L))
        // A career reset clears BOTH registers.
        prefs.resetLedger()
        assertTrue(prefs.readLedger().games == 0)
        assertTrue(prefs.readDecisionLedger().isEmpty)
    }
}
