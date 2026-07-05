package com.kursi.ai

import com.kursi.engine.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Sanity tests for [OpponentInsight] — the PUBLIC-info per-opponent dossier the UI renders.
 *
 * Asserts the derived posteriors are well-formed (sum to 1, each in [0,1]), pHolds stays in [0,1],
 * claim/bluff counters track the public history, and a face-up reveal pushes that role's posterior
 * down (the deduction is visible to the human's coach). SECRECY: everything here is built from a
 * [BotMemory] fed ONLY public events + a redacted [PlayerView], never an opponent's hidden cards.
 */
class OpponentInsightTest {
    @Test
    fun posteriors_are_wellFormed_and_pHolds_inRange() {
        val config = GameConfig.forPlayers(4)
        val seed = 4242L
        // Drive a real game partway with deterministic Hard bots, feeding a coach memory the
        // PUBLIC events (exactly what GameSession does for the human's MoveAdvisor).
        var state = initialState(config, seed)
        val memory = BotMemory()
        val human = PlayerId(0)
        val policies: Map<PlayerId, Policy> =
            (0 until 4).associate {
                PlayerId(it) to HardPolicy(seed + it * 17L)
            }

        var steps = 0
        while (state.phase !is Phase.GameOver && steps++ < 120) {
            val who = whoActsNext(state) ?: break
            val legal = legalIntents(state, who)
            if (legal.isEmpty()) break
            val intent = policies.getValue(who).decide(redact(state, who), legal)
            val outcome = applyIntent(state, intent)
            if (outcome !is ApplyOutcome.Accepted) break
            state = outcome.state
            outcome.events.forEach { memory.observe(it, state.turnNumber) }
        }

        val view = redact(state, human)
        val insights = OpponentInsight.forAll(view, memory)
        assertTrue(insights.isNotEmpty(), "expected at least one opponent dossier")
        assertTrue(insights.none { it.opponentId == human }, "viewer must not appear in its own insights")

        for (ins in insights) {
            // Posterior is a probability distribution over the ACTIVE roles only. On a 4-player table
            // PATRAKAAR (the 6th role) is NOT in the deck, so it must NOT appear in the posterior —
            // coverage is config.activeRoles (5 here), not all of Role.entries (6).
            val sum = ins.posterior.values.sum()
            assertEquals(view.config.activeRoles.size, ins.posterior.size, "posterior must cover every active role")
            assertTrue(abs(sum - 1.0) < 1e-6, "posterior for seat ${ins.seatIndex} must sum to 1, was $sum")
            ins.posterior.values.forEach {
                assertTrue(it in 0.0..1.0, "posterior prob $it out of [0,1] for seat ${ins.seatIndex}")
            }
            // pHolds: per-role independent probabilities, each a valid probability.
            ins.pHolds.values.forEach {
                assertTrue(it in 0.0..1.0, "pHolds $it out of [0,1] for seat ${ins.seatIndex}")
            }
            // Style scalars in their declared ranges.
            assertTrue(ins.bluffRate in 0.0..0.8, "bluffRate ${ins.bluffRate} out of range")
            assertTrue(ins.style.aggression in 0.0..1.0)
            assertTrue(ins.style.challengeRate in 0.0..1.0)
            // Counters are non-negative and consistent.
            assertTrue(ins.totalClaims >= 0 && ins.bluffsCaught >= 0)
            assertTrue(ins.bluffsCaught <= ins.totalClaims, "bluffsCaught can't exceed totalClaims")
            assertNotNull(ins.mostLikelyRole)
        }
    }

    @Test
    fun faceUp_reveal_depresses_that_roles_posterior() {
        // Construct a deterministic view where opponent seat 1 has revealed a NETA face-up.
        // The coach (no extra evidence) should rate NETA in seat 1's HIDDEN slot below the
        // base prior, because a copy is now publicly accounted for.
        val config = GameConfig.forPlayers(3)
        val viewer = PlayerId(0)
        val opp =
            OpponentView(
                id = PlayerId(1),
                seatIndex = 1,
                coins = 2,
                faceUpRoles = listOf(Role.NETA),
                faceDownCount = 1,
                eliminated = false,
            )
        val opp2 =
            OpponentView(
                id = PlayerId(2),
                seatIndex = 2,
                coins = 2,
                faceUpRoles = emptyList(),
                faceDownCount = 2,
                eliminated = false,
            )
        val view =
            PlayerView(
                viewer = viewer,
                config = config,
                treasury = 40,
                deckCount = 5,
                turnNumber = 3,
                myCoins = 2,
                myInfluence = listOf(Role.BHAI, Role.BABU),
                myFaceUp = emptyList(),
                myCards =
                    listOf(
                        OwnCard(CardId(0), Role.BHAI, false),
                        OwnCard(CardId(1), Role.BABU, false),
                    ),
                players =
                    listOf(
                        OpponentView(viewer, 0, 2, emptyList(), 2, false),
                        opp,
                        opp2,
                    ),
                phase = PhaseView.Turn(viewer),
            )

        val insight = OpponentInsight.from(view, BotMemory(), PlayerId(1))
        assertNotNull(insight)
        val pNeta = insight.posterior.getValue(Role.NETA)
        // Uniform baseline is over the ACTIVE roles (5 on a 3-player table), not all 6 — PATRAKAAR is
        // absent from small-table decks and never enters the posterior.
        val uniform = 1.0 / config.activeRoles.size
        assertTrue(
            pNeta < uniform,
            "with a NETA already face-up on seat 1, its hidden-slot NETA posterior ($pNeta) " +
                "should sit below the uniform baseline ($uniform)",
        )
    }
}
