package com.kursi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** §2 + §6.3 — the deck-scaling table, golden determinism, and a SimHarness balance smoke. */
class ScalingGoldenTest {
    /**
     * THE SINGLE SOURCE OF TRUTH for the deck-scaling ladder (docs/02 reconciled here).
     *
     * Levers, introduced in order: (1) uniform copies/role, capped at [GameConfig.MAX_COPIES_PER_ROLE]=5 so
     * big tables aren't challenge-dead; (2) a 6th role (PATRAKAAR) entering the deck at
     * N >= [GameConfig.PATRAKAAR_MIN_PLAYERS]=9. Roles stay equiprobable (uniform copies across activeRoles).
     *
     * (Doc §0/§1 listed N=2 as C3/deck15 under an unbounded 5-role model; the engine's actual tuned ladder
     * — clamped copies + the 6th-role step — is THIS table. The doc's N=2 figure is the reconciled inconsistency.)
     */
    @Test
    fun deck_composition_ladder_2_to_10() {
        // N -> (roleCount, copiesPerRole, deckSize). PATRAKAAR (6th role) enters at N>=9.
        val expected =
            mapOf(
                2 to Triple(5, 4, 20),
                3 to Triple(5, 5, 25),
                4 to Triple(5, 5, 25),
                5 to Triple(5, 5, 25),
                6 to Triple(5, 5, 25),
                7 to Triple(5, 5, 25),
                8 to Triple(5, 5, 25),
                9 to Triple(6, 5, 30), // 6th role enters; copies stay capped at 5
                10 to Triple(6, 5, 30),
            )
        for ((n, exp) in expected) {
            val (roles, copies, deck) = exp
            val c = GameConfig.forPlayers(n)
            assertEquals(roles, c.roleCount, "roleCount for N=$n")
            assertEquals(copies, c.copiesPerRole, "copiesPerRole for N=$n")
            assertEquals(deck, c.deckSize, "deckSize for N=$n")
            // copies never exceed the big-table cap.
            assertTrue(c.copiesPerRole <= GameConfig.MAX_COPIES_PER_ROLE, "copies cap for N=$n")
            // Equiprobability: deck is a uniform multiset over exactly the active roles.
            assertEquals(c.roleCount * c.copiesPerRole, c.deckSize, "deck = roles*copies for N=$n")
            // The 6th role is present iff N>=9.
            assertEquals(n >= GameConfig.PATRAKAAR_MIN_PLAYERS, Role.PATRAKAAR in c.activeRoles, "PATRAKAAR membership N=$n")
            assertTrue(c.activeRoles.containsAll(baseRoles), "classic five always present N=$n")
            // No-starvation: the court deck after dealing covers the Setting/redraw buffer floor.
            assertTrue(c.deckSize - n * c.influencePerPlayer >= c.bufferFloor, "court buffer for N=$n")
        }
    }

    @Test
    fun patrakaar_is_absent_below_threshold_and_present_at_or_above() {
        for (n in 2..8) {
            val c = GameConfig.forPlayers(n)
            assertTrue(Role.PATRAKAAR !in c.activeRoles, "PATRAKAAR must NOT be in deck at N=$n")
            // And the engine actually deals a PATRAKAAR-free deck.
            val s = initialState(c, seed = 1L)
            assertTrue(s.cards.values.none { it == Role.PATRAKAAR }, "no PATRAKAAR card dealt at N=$n")
        }
        for (n in 9..10) {
            val c = GameConfig.forPlayers(n)
            assertTrue(Role.PATRAKAAR in c.activeRoles, "PATRAKAAR must be in deck at N=$n")
            val s = initialState(c, seed = 1L)
            assertEquals(c.copiesPerRole, s.cards.values.count { it == Role.PATRAKAAR }, "PATRAKAAR copies at N=$n")
        }
    }

    @Test
    fun golden_game_is_reproducible_n2() {
        val config = GameConfig.forPlayers(2)
        val pol1 = { pid: PlayerId -> RandomLegalPolicy(pid.raw.toLong() + 1000) }
        val a = SimHarness.playOut(config, seed = 2024L, policies = (0 until 2).associate { PlayerId(it) to pol1(PlayerId(it)) })
        val b = SimHarness.playOut(config, seed = 2024L, policies = (0 until 2).associate { PlayerId(it) to pol1(PlayerId(it)) })
        assertEquals(a.winner, b.winner)
        assertEquals(a.turns, b.turns)
        assertEquals(a.steps, b.steps)
    }

    @Test
    fun golden_game_completes_n10_big_table() {
        val config = GameConfig.forPlayers(10)
        val result =
            SimHarness.playOut(
                config,
                seed = 555L,
                policies = (0 until 10).associate { PlayerId(it) to RandomLegalPolicy(it * 7L + 3) },
            )
        assertTrue(result.turns >= 1)
        assertTrue(result.winner.raw in 0..9)
    }

    @Test
    fun sim_harness_balance_smoke() {
        // Not the full 10k/N gate — a fast sanity run proving every game terminates with exactly one winner.
        val stats =
            SimHarness.playMany(
                GameConfig.forPlayers(4),
                seeds = 1L..120L,
                policyFactory = { _, seed -> RandomLegalPolicy(seed) },
            )
        assertEquals(120, stats.games)
        assertEquals(120, stats.winsBySeat.values.sum())
        assertTrue(stats.avgTurns > 0.0)
        // Every seat should win at least once across 120 games (no structurally dead seat).
        assertEquals(4, stats.winsBySeat.keys.size, "some seat never won: ${stats.winsBySeat}")
    }
}
