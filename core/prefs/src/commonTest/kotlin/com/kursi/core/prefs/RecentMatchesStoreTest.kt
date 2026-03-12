package com.kursi.core.prefs

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M6c §1 — the completed-match replay store: prepends finished-match records (most-recent first),
 * caps to [AppPrefs.MAX_RECENT_MATCHES], survives a fresh AppPrefs over the same backing store, and
 * is wiped by a career reset.
 */
class RecentMatchesStoreTest {

    @Test
    fun freshStore_isEmpty() {
        val prefs = AppPrefs(MapSettings())
        assertTrue(prefs.recentMatches().isEmpty())
        assertTrue(prefs.recentMatchesFlow.value.isEmpty())
    }

    @Test
    fun addRecentMatch_prependsMostRecentFirst() {
        val prefs = AppPrefs(MapSettings())
        prefs.addRecentMatch("a")
        prefs.addRecentMatch("b")
        prefs.addRecentMatch("c")
        assertEquals(listOf("c", "b", "a"), prefs.recentMatches())
        assertEquals(listOf("c", "b", "a"), prefs.recentMatchesFlow.value)
    }

    @Test
    fun addRecentMatch_capsToMax() {
        val prefs = AppPrefs(MapSettings())
        // Insert MAX + 5 records; only the most-recent MAX survive, newest first.
        val total = AppPrefs.MAX_RECENT_MATCHES + 5
        for (i in 0 until total) prefs.addRecentMatch("m$i")
        val kept = prefs.recentMatches()
        assertEquals(AppPrefs.MAX_RECENT_MATCHES, kept.size)
        assertEquals("m${total - 1}", kept.first())
        assertEquals("m${total - AppPrefs.MAX_RECENT_MATCHES}", kept.last())
    }

    @Test
    fun blankRecord_isIgnored() {
        val prefs = AppPrefs(MapSettings())
        prefs.addRecentMatch("x")
        prefs.addRecentMatch("")
        prefs.addRecentMatch("   ")
        assertEquals(listOf("x"), prefs.recentMatches())
    }

    @Test
    fun survivesFreshAppPrefsOverSameStore() {
        val settings = MapSettings()
        AppPrefs(settings).apply {
            addRecentMatch("one")
            addRecentMatch("two")
        }
        // A new AppPrefs reading the same backing store sees the persisted list.
        val reopened = AppPrefs(settings)
        assertEquals(listOf("two", "one"), reopened.recentMatches())
    }

    @Test
    fun clearRecentMatches_wipesAll() {
        val prefs = AppPrefs(MapSettings())
        prefs.addRecentMatch("one")
        prefs.addRecentMatch("two")
        prefs.clearRecentMatches()
        assertTrue(prefs.recentMatches().isEmpty())
        assertTrue(prefs.recentMatchesFlow.value.isEmpty())
    }

    @Test
    fun resetLedger_alsoClearsRecentMatches() {
        val prefs = AppPrefs(MapSettings())
        prefs.addRecentMatch("one")
        prefs.resetLedger()
        assertTrue(prefs.recentMatches().isEmpty())
    }
}
