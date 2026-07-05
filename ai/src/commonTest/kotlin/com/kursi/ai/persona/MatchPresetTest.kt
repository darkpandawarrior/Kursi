package com.kursi.ai.persona

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MatchPreset (M5 ONBOARD §2) — verifies each curated preset deals a sane, deterministic lineup.
 *
 * The presets are a thin pre-fill over [PersonaAssigner.assign], so the contract we care about is:
 *  - the lineup has exactly playerCount-1 opponents (seat 0 is the human),
 *  - every opponent is distinct (no duplicate persona at one table),
 *  - the lineup is fully deterministic (same preset → identical roster every call).
 */
class MatchPresetTest {
    @Test
    fun every_preset_deals_the_right_count_of_distinct_personas() {
        for (preset in MatchPreset.ALL) {
            val lineup = preset.lineup()
            assertEquals(
                preset.playerCount - 1,
                lineup.size,
                "preset ${preset.id} should deal ${preset.playerCount - 1} opponents",
            )
            val ids = lineup.map { it.id }
            assertEquals(ids.size, ids.toSet().size, "preset ${preset.id} dealt a duplicate persona")
        }
    }

    @Test
    fun lineups_are_deterministic() {
        for (preset in MatchPreset.ALL) {
            val a = preset.lineup().map { it.id }
            val b = preset.lineup().map { it.id }
            assertEquals(a, b, "preset ${preset.id} lineup is not deterministic")
        }
    }

    @Test
    fun chaos_ten_fills_the_whole_roster() {
        // The 10-seat preset faces 9 opponents — the entire 10-persona roster minus the human seat.
        val lineup = MatchPreset.CHAOS_TEN.lineup()
        assertEquals(9, lineup.size)
        assertTrue(lineup.map { it.id }.toSet().size == 9)
    }
}
