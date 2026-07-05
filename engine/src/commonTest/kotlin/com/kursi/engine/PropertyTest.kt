package com.kursi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** §6.2 — properties P1..P9 over fuzzed games driven by [RandomLegalPolicy]. */
class PropertyTest {
    private fun policies(
        config: GameConfig,
        seed: Long,
    ): Map<PlayerId, Policy> = (0 until config.seatCount).associate { PlayerId(it) to RandomLegalPolicy(seed * 131 + it) }

    /** Plays a fuzzed game, recording every intent and checking invariants after every transition. */
    private fun recordGame(
        config: GameConfig,
        seed: Long,
    ): Pair<GameState, List<Intent>> {
        var state = initialState(config, seed)
        checkInvariants(state)
        val pol = policies(config, seed)
        val intents = ArrayList<Intent>()
        var steps = 0
        while (state.phase !is Phase.GameOver) {
            if (++steps > 200_000) fail("game $config/$seed did not terminate")
            val who = whoActsNext(state) ?: fail("no actor in ${state.phase}")
            val legal = legalIntents(state, who)
            if (legal.isEmpty()) fail("no legal intents for $who in ${state.phase}")
            val intent = pol.getValue(who).decide(redact(state, who), legal)
            intents.add(intent)
            state = (applyIntent(state, intent) as ApplyOutcome.Accepted).state
            checkInvariants(state) // P1/P2/P3: invariants hold after every reduce
        }
        return state to intents
    }

    @Test
    fun p1_p2_p3_p8_p9_invariants_termination_single_winner() {
        for (n in 2..10) {
            val config = GameConfig.forPlayers(n)
            for (seed in 1L..80L) {
                val (end, _) = recordGame(config, seed)
                val alive = end.alivePlayers()
                assertEquals(1, alive.size, "P9: more than one survivor in $config/$seed")
                assertEquals((end.phase as Phase.GameOver).winner, alive.first().id, "P9/P10: winner mismatch")
            }
        }
    }

    @Test
    fun p5_p6_determinism_replay_by_intent() {
        for (n in intArrayOf(2, 3, 4, 6, 8, 10)) {
            val config = GameConfig.forPlayers(n)
            for (seed in 1L..12L) {
                val (end, intents) = recordGame(config, seed)
                var s = initialState(config, seed)
                for (i in intents) s = (applyIntent(s, i) as ApplyOutcome.Accepted).state
                assertEquals(end, s, "P5/P6: replay of (seed=$seed,n=$n) diverged from the live game")
            }
        }
    }

    @Test
    fun p4_redaction_is_a_strict_subset_projection() {
        // For every viewer, the view exposes the viewer's own face-down roles and nothing of anyone else's.
        for (n in 3..6) {
            val state = initialState(GameConfig.forPlayers(n), seed = 42L + n)
            for (seat in 0 until n) {
                val viewer = PlayerId(seat)
                val view = redact(state, viewer)
                assertEquals(
                    state.faceDownInfluence(viewer).map { state.cards.getValue(it) }.sortedBy { it.ordinal },
                    view.myInfluence,
                    "viewer must see exactly their own hidden roles",
                )
                // OpponentView carries no hidden-role field at all — leak is impossible by construction.
                assertEquals(n, view.players.size)
                assertTrue(view.players.all { it.faceDownCount in 0..2 })
            }
        }
    }

    @Test
    fun p4_indistinguishability_of_other_players_hidden_cards() {
        // Two states differing only in a non-viewer's hidden card identities must redact identically.
        val config = GameConfig.forPlayers(4)
        val s = initialState(config, seed = 7L)
        val viewer = PlayerId(0)
        // pick another player's face-down card and a deck card of a different role; swap their role assignment.
        val other = PlayerId(1)
        val otherCard = s.faceDownInfluence(other).first()
        val otherRole = s.cards.getValue(otherCard)
        val deckCard = s.deckCards.first { s.cards.getValue(it) != otherRole }
        val deckRole = s.cards.getValue(deckCard)
        val swapped = s.copy(cards = s.cards + mapOf(otherCard to deckRole, deckCard to otherRole))
        assertEquals(
            redact(s, viewer),
            redact(swapped, viewer),
            "viewer's projection must not depend on another player's hidden card identity",
        )
    }

    @Test
    fun p7_legal_intents_are_all_accepted_and_illegal_rejected() {
        // Walk a fuzzed game; at each decision point assert EVERY enumerated legal intent is Accepted.
        val config = GameConfig.forPlayers(4)
        var state = initialState(config, seed = 99L)
        val pol = policies(config, 99L)
        var steps = 0
        while (state.phase !is Phase.GameOver && steps < 400) {
            steps++
            val who = whoActsNext(state)!!
            val legal = legalIntents(state, who)
            for (intent in legal) {
                assertTrue(applyIntent(state, intent) is ApplyOutcome.Accepted, "legal intent rejected: $intent")
            }
            // An intent from the wrong player is always rejected.
            val other = (0 until config.seatCount).map { PlayerId(it) }.first { it != who }
            assertTrue(applyIntent(state, Intent.Pass(other)) is ApplyOutcome.Rejected)
            state = (applyIntent(state, pol.getValue(who).decide(redact(state, who), legal)) as ApplyOutcome.Accepted).state
        }
    }
}
