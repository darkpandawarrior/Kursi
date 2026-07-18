package com.kursi.core.prefs

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guided-funnel (spec §6) and graduation-policy (spec §3) prefs: [AppPrefs.hasSeenFunnel]'s
 * upgrade-safety default, and [AppPrefs.densityLayerManuallySet] as a plain persisted flag.
 */
class FunnelAndGraduationPrefsTest {
    @Test
    fun freshInstall_hasNotSeenFunnel() {
        // Neither flag ever set — a genuinely brand-new player sees the funnel.
        val prefs = AppPrefs(MapSettings())
        assertFalse(prefs.hasSeenFunnel)
    }

    @Test
    fun upgradingPlayer_whoAlreadySawTheOldOfferDialog_skipsTheFunnel() {
        // A pre-existing install already resolved the old post-primer offer (hasSeenTutorialOffer=true)
        // but never wrote the new hasSeenFunnel key — the default must derive from the legacy flag so
        // returning players are never routed into the funnel retroactively.
        val prefs = AppPrefs(MapSettings())
        prefs.hasSeenTutorialOffer = true
        assertTrue(prefs.hasSeenFunnel)
    }

    @Test
    fun explicitWrite_winsOverTheDerivedDefault() {
        val prefs = AppPrefs(MapSettings())
        prefs.hasSeenTutorialOffer = true // would default hasSeenFunnel to true
        prefs.hasSeenFunnel = false // but an explicit write always wins
        assertFalse(prefs.hasSeenFunnel)
    }

    @Test
    fun densityLayerManuallySet_defaultsFalse_andPersists() {
        val store = MapSettings()
        assertFalse(AppPrefs(store).densityLayerManuallySet)
        AppPrefs(store).densityLayerManuallySet = true
        assertTrue(AppPrefs(store).densityLayerManuallySet)
    }
}
