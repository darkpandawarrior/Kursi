package com.kursi.core.prefs

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M6e — the gauntlet ladder progress (Tarakki ki Seedhi): a win on the current target rung promotes,
 * a win on an already-cleared rung never regresses progress, a loss never regresses, and the whole
 * thing survives a fresh AppPrefs over the same backing store.
 */
class GauntletProgressTest {

    @Test
    fun freshProgress_isAtBottom() {
        val prefs = AppPrefs(MapSettings())
        val g = prefs.readGauntlet()
        assertEquals(-1, g.clearedRung)
        assertEquals(0, g.wins)
        assertTrue(g.isFresh)
        assertEquals(0, g.targetRung)
        assertEquals(0, g.clearedCount)
        assertFalse(g.isConquered(4))
    }

    @Test
    fun winOnTargetRung_promotes() {
        val prefs = AppPrefs(MapSettings())
        val g0 = prefs.recordGauntletResult(rung = 0, won = true)
        assertEquals(0, g0.clearedRung)
        assertEquals(1, g0.wins)
        assertEquals(1, g0.targetRung)
        // Next target is rung 1.
        val g1 = prefs.recordGauntletResult(rung = 1, won = true)
        assertEquals(1, g1.clearedRung)
        assertEquals(2, g1.wins)
    }

    @Test
    fun lossNeverRegresses_butReattemptable() {
        val prefs = AppPrefs(MapSettings())
        prefs.recordGauntletResult(rung = 0, won = true) // cleared 0
        val afterLoss = prefs.recordGauntletResult(rung = 1, won = false)
        assertEquals(0, afterLoss.clearedRung) // still cleared 0, target stays 1
        assertEquals(1, afterLoss.wins)        // a loss doesn't bump wins
        // Re-attempt rung 1 and win → promotes.
        val afterWin = prefs.recordGauntletResult(rung = 1, won = true)
        assertEquals(1, afterWin.clearedRung)
        assertEquals(2, afterWin.wins)
    }

    @Test
    fun reWinningAClearedRung_bumpsWinsButNotProgress() {
        val prefs = AppPrefs(MapSettings())
        prefs.recordGauntletResult(rung = 0, won = true)
        prefs.recordGauntletResult(rung = 1, won = true) // cleared 1
        val reWin = prefs.recordGauntletResult(rung = 0, won = true) // replay an old rung
        assertEquals(1, reWin.clearedRung) // unchanged
        assertEquals(3, reWin.wins)        // still counts as a win
    }

    @Test
    fun conquered_atLastIndex() {
        val prefs = AppPrefs(MapSettings())
        for (rung in 0..4) prefs.recordGauntletResult(rung = rung, won = true)
        val g = prefs.readGauntlet()
        assertEquals(4, g.clearedRung)
        assertTrue(g.isConquered(4))
        assertEquals(5, g.clearedCount)
    }

    @Test
    fun progress_survivesFreshPrefs() {
        val store = MapSettings()
        AppPrefs(store).recordGauntletResult(rung = 0, won = true)
        AppPrefs(store).recordGauntletResult(rung = 1, won = true)
        val reopened = AppPrefs(store).readGauntlet()
        assertEquals(1, reopened.clearedRung)
        assertEquals(2, reopened.wins)
    }

    @Test
    fun resetLedger_wipesGauntlet() {
        val prefs = AppPrefs(MapSettings())
        prefs.recordGauntletResult(rung = 0, won = true)
        prefs.resetLedger()
        val g = prefs.readGauntlet()
        assertEquals(-1, g.clearedRung)
        assertEquals(0, g.wins)
    }
}
