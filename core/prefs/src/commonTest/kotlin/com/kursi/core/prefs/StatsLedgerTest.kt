package com.kursi.core.prefs

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lifetime stats ledger (M3 §3) — folds completed games into persisted totals + per-persona
 * head-to-head, survives a fresh AppPrefs over the same backing store, and round-trips the
 * delimited H2H codec.
 */
class StatsLedgerTest {
    @Test
    fun freshLedger_isAllZero() {
        val prefs = AppPrefs(MapSettings())
        val l = prefs.readLedger()
        assertEquals(0, l.games)
        assertEquals(0, l.wins)
        assertEquals(0, l.bluffsHeld)
        assertEquals(0, l.bluffsCaught)
        assertEquals(0f, l.winRate)
        assertTrue(l.headToHead.isEmpty())
    }

    @Test
    fun recordGame_accumulatesTotalsAndH2H() {
        val prefs = AppPrefs(MapSettings())

        // Game 1: human wins vs bhai_teja + babu_filewala.
        prefs.recordGame(
            humanWon = true,
            bluffsHeld = 2,
            bluffsCaught = 1,
            opponentIds = listOf("bhai_teja", "babu_filewala"),
        )
        // Game 2: human loses vs bhai_teja + netaji_vachan.
        val after =
            prefs.recordGame(
                humanWon = false,
                bluffsHeld = 1,
                bluffsCaught = 3,
                opponentIds = listOf("bhai_teja", "netaji_vachan"),
            )

        assertEquals(2, after.games)
        assertEquals(1, after.wins)
        assertEquals(1, after.losses)
        assertEquals(3, after.bluffsHeld) // 2 + 1
        assertEquals(4, after.bluffsCaught) // 1 + 3
        assertEquals(0.5f, after.winRate)

        // bhai_teja faced twice, won once.
        val bhai = after.headToHead.getValue("bhai_teja")
        assertEquals(2, bhai.played)
        assertEquals(1, bhai.wins)
        // babu only in the won game.
        val babu = after.headToHead.getValue("babu_filewala")
        assertEquals(1, babu.played)
        assertEquals(1, babu.wins)
        // netaji only in the lost game.
        val neta = after.headToHead.getValue("netaji_vachan")
        assertEquals(1, neta.played)
        assertEquals(0, neta.wins)
    }

    @Test
    fun ledger_persistsAcrossAppPrefsInstances() {
        val backing = MapSettings()
        AppPrefs(backing).recordGame(
            humanWon = true,
            bluffsHeld = 5,
            bluffsCaught = 0,
            opponentIds = listOf("vakil_loophole"),
        )

        // A brand-new AppPrefs over the SAME store must see the persisted ledger.
        val reread = AppPrefs(backing).readLedger()
        assertEquals(1, reread.games)
        assertEquals(1, reread.wins)
        assertEquals(5, reread.bluffsHeld)
        assertEquals(1, reread.headToHead.getValue("vakil_loophole").played)
    }

    @Test
    fun resetLedger_clearsEverything() {
        val prefs = AppPrefs(MapSettings())
        prefs.recordGame(true, 1, 1, listOf("dalla_tiwari"))
        prefs.resetLedger()
        val l = prefs.readLedger()
        assertEquals(0, l.games)
        assertTrue(l.headToHead.isEmpty())
    }

    @Test
    fun matchSnapshot_roundTripsAndClears() {
        val prefs = AppPrefs(MapSettings())
        assertNull(prefs.matchSnapshot)
        prefs.matchSnapshot = "seed=42|log=Income,Tax"
        assertEquals("seed=42|log=Income,Tax", prefs.matchSnapshot)
        prefs.clearMatchSnapshot()
        assertNull(prefs.matchSnapshot)
    }
}
