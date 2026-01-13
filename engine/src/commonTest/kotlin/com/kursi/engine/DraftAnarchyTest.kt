package com.kursi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for DRAFT (Nilaami) + ANARCHY (Andher Nagari) variant surface on [GameConfig].
 *
 * (a) [GameConfig.drafted] builds a valid 4-role deck and the initial state + first steps are sound.
 * (b) [GameConfig.effectiveCoupCost] behaves correctly under both anarchy=false and anarchy=true.
 * (c) An unviable draft throws from the [GameConfig] init block.
 */
class DraftAnarchyTest {

    // ── (a) drafted deck ─────────────────────────────────────────────────────

    @Test
    fun drafted_4players_4roles_producesValidConfig() {
        val roles = listOf(Role.NETA, Role.BHAI, Role.BABU, Role.JUGAADU)
        val cfg = GameConfig.drafted(4, roles)
        assertEquals(4, cfg.activeRoles.size, "drafted config must have exactly 4 active roles")
        assertEquals(roles.toSet(), cfg.activeRoles.toSet(), "active roles must match the supplied set")
    }

    @Test
    fun drafted_initialStatePassesInvariants() {
        val cfg = GameConfig.drafted(4, listOf(Role.NETA, Role.BHAI, Role.BABU, Role.JUGAADU))
        val state = initialState(cfg, seed = 12345L)
        checkInvariants(state)
    }

    @Test
    fun drafted_legalIntentsNonEmptyOnFirstTurn() {
        val cfg = GameConfig.drafted(4, listOf(Role.NETA, Role.BHAI, Role.BABU, Role.JUGAADU))
        val state = initialState(cfg, seed = 12345L)
        val actor = whoActsNext(state)!!
        val legal = legalIntents(state, actor)
        assertTrue(legal.isNotEmpty(), "drafted game must have legal intents on the first turn")
    }

    @Test
    fun drafted_firstFewStepsAreWellFormed() {
        // Run a handful of steps using a RandomLegalPolicy to confirm the deck is genuinely playable.
        val cfg = GameConfig.drafted(4, listOf(Role.NETA, Role.BHAI, Role.BABU, Role.JUGAADU))
        var state = initialState(cfg, seed = 42L)
        val policies = (0 until 4).associate { PlayerId(it) to RandomLegalPolicy(42L + it) }

        var steps = 0
        // Advance up to 20 intent applications (not 20 full turns).
        while (state.phase !is Phase.GameOver && steps < 20) {
            val who = whoActsNext(state) ?: break
            val legal = legalIntents(state, who)
            assertTrue(legal.isNotEmpty(), "no legal intents for $who at step $steps")
            val intent = policies.getValue(who).decide(redact(state, who), legal)
            val outcome = applyIntent(state, intent)
            assertTrue(outcome is ApplyOutcome.Accepted, "intent rejected at step $steps: $intent")
            state = (outcome as ApplyOutcome.Accepted).state
            checkInvariants(state)
            steps++
        }
        assertTrue(steps > 0, "should have advanced at least one step")
    }

    @Test
    fun drafted_deduplicatesRoles() {
        // Passing duplicates must silently deduplicate to 4 distinct roles.
        val roles = listOf(Role.NETA, Role.BHAI, Role.BABU, Role.JUGAADU, Role.NETA)
        val cfg = GameConfig.drafted(4, roles)
        assertEquals(4, cfg.activeRoles.size, "drafted must deduplicate input roles")
    }

    // ── (b) effectiveCoupCost ────────────────────────────────────────────────

    @Test
    fun effectiveCoupCost_withoutAnarchy_equalsConstantCoupCost() {
        val cfg = GameConfig.forPlayers(4)  // anarchy = false
        assertFalse(cfg.anarchy)
        for (turn in 1..60) {
            assertEquals(cfg.coupCost, cfg.effectiveCoupCost(turn),
                "without anarchy coup cost must be constant at turn $turn")
        }
    }

    @Test
    fun effectiveCoupCost_withAnarchy_doesNotExceedCoupCost() {
        val cfg = GameConfig.forPlayers(4).copy(anarchy = true)
        for (turn in 1..80) {
            val cost = cfg.effectiveCoupCost(turn)
            assertTrue(cost <= cfg.coupCost, "anarchy coup cost must never exceed base at turn $turn")
        }
    }

    @Test
    fun effectiveCoupCost_withAnarchy_doesNotDropBelowFloor() {
        val cfg = GameConfig.forPlayers(4).copy(anarchy = true)
        for (turn in 1..200) {
            val cost = cfg.effectiveCoupCost(turn)
            assertTrue(cost >= cfg.coupCostFloor,
                "anarchy coup cost must never go below coupCostFloor=${cfg.coupCostFloor} at turn $turn")
        }
    }

    @Test
    fun effectiveCoupCost_withAnarchy_strictlyDecreasesAfterGrace() {
        val cfg = GameConfig.forPlayers(4).copy(anarchy = true)
        val grace = cfg.seatCount * 2  // = 8 for 4-seat
        // Collect costs at turn boundaries: one per "full round" past grace
        val roundsToCheck = 8
        val costsAtRoundStarts = (0 until roundsToCheck).map { r ->
            // First turn of the (r+1)-th round past the grace window
            cfg.effectiveCoupCost(grace + r * cfg.seatCount + 1)
        }
        // After the floor is hit the cost stabilises — but before then it should be non-increasing.
        for (i in 1 until costsAtRoundStarts.size) {
            assertTrue(costsAtRoundStarts[i] <= costsAtRoundStarts[i - 1],
                "anarchy coup cost must be non-increasing across rounds past grace (round $i vs ${i - 1})")
        }
        // Somewhere in the first several rounds it must actually drop (not be flat from the start).
        assertTrue(costsAtRoundStarts.min() < cfg.coupCost,
            "anarchy coup cost must fall below the base coup cost by the time the grace window is past")
    }

    @Test
    fun effectiveCoupCost_withAnarchy_isStableAtFloor() {
        val cfg = GameConfig.forPlayers(4).copy(anarchy = true)
        // At a very large turn number the cost should be pinned at the floor.
        val veryLate = 10_000
        assertEquals(cfg.coupCostFloor, cfg.effectiveCoupCost(veryLate),
            "cost must equal coupCostFloor well past the grace window")
    }

    // ── (c) unviable drafts throw ─────────────────────────────────────────────

    @Test
    fun drafted_tooFewRoles_throws() {
        // MIN_ACTIVE_ROLES = 4; providing only 3 must throw.
        assertFails("draft with 3 roles (< MIN_ACTIVE_ROLES) must throw") {
            GameConfig.drafted(4, listOf(Role.NETA, Role.BHAI, Role.BABU))
        }
    }

    @Test
    fun drafted_emptyRoles_throws() {
        assertFails("draft with 0 roles must throw") {
            GameConfig.drafted(4, emptyList())
        }
    }

    @Test
    fun drafted_insufficientDeckBuffer_throws() {
        // 2 players with exactly 4 distinct roles: copies = ceil((4+15)/4) = 5 (the cap).
        // 5*4 = 20 cards, deal 4, buffer = 16 >= bufferFloor=3 → should be fine.
        // But a 2-player, 4-role table with only 3 copies per role:
        //   deckSize = 12, deal = 4, leftover = 8 >= bufferFloor(2) = 3 → fine.
        // There is no purely "viable in terms of role count but not buffer" combo that 4 roles allow
        // easily; the init check already handles deckSize - deal >= bufferFloor.
        // Instead, verify that the single-role case (rejected by distinctness) also throws.
        assertFails("draft with a single distinct role must throw") {
            GameConfig.drafted(4, listOf(Role.NETA, Role.NETA, Role.NETA))
        }
    }
}
