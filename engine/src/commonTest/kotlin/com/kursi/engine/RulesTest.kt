package com.kursi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** §6.1 — the engine spec's edge-case rulings, each pinned by a focused unit test. */
class RulesTest {
    private val c2 = cfg(2)

    @Test
    fun dehaadi_income_adds_one_and_is_unblockable() {
        val s0 = buildState(c2, listOf(listOf(Role.NETA, Role.BHAI), listOf(Role.VAKIL, Role.BABU)), listOf(2, 2))
        val s = applyIntent(s0, Intent.DeclareAction(P[0], Action.Income)).ok()
        assertEquals(3, s.player(P[0]).coins)
        assertTrue(s.phase is Phase.AwaitingAction) // resolved immediately, no reaction window
        assertEquals(1, (s.phase as Phase.AwaitingAction).actorSeat)
    }

    @Test
    fun fdi_blocked_by_neta_then_unchallenged_fizzles() {
        val s0 = buildState(c2, listOf(listOf(Role.BHAI, Role.BABU), listOf(Role.NETA, Role.JUGAADU)), listOf(2, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.ForeignAid)).ok()
        assertEquals(P[1], whoActsNext(s)) // block window, target-agnostic: all opponents
        s = applyIntent(s, Intent.Block(P[1], Role.NETA)).ok()
        assertEquals(P[0], whoActsNext(s)) // challenge-the-block window
        s = applyIntent(s, Intent.Pass(P[0])).ok()
        assertEquals(2, s.player(P[0]).coins) // FDI negated
    }

    @Test
    fun fdi_unblocked_adds_two() {
        val s0 = buildState(c2, listOf(listOf(Role.BHAI, Role.BABU), listOf(Role.NETA, Role.JUGAADU)), listOf(2, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.ForeignAid)).ok()
        s = applyIntent(s, Intent.Pass(P[1])).ok()
        assertEquals(4, s.player(P[0]).coins)
    }

    @Test
    fun ghotala_tax_unchallenged_adds_three() {
        val s0 = buildState(c2, listOf(listOf(Role.NETA, Role.BHAI), listOf(Role.VAKIL, Role.BABU)), listOf(2, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.Tax)).ok()
        s = applyIntent(s, Intent.Pass(P[1])).ok()
        assertEquals(5, s.player(P[0]).coins)
    }

    @Test
    fun challenge_truthful_claim_costs_the_challenger() {
        val s0 = buildState(c2, listOf(listOf(Role.NETA, Role.BHAI), listOf(Role.VAKIL, Role.BABU)), listOf(2, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.Tax)).ok()
        s = applyIntent(s, Intent.Challenge(P[1])).ok()
        assertTrue(s.phase is Phase.AwaitingInfluenceLoss)
        assertEquals(P[1], (s.phase as Phase.AwaitingInfluenceLoss).loser)
        s = applyIntent(s, legalIntents(s, P[1]).first()).ok()
        assertEquals(5, s.player(P[0]).coins) // tax still happened
        assertEquals(2, s.influenceCount(P[0])) // prover revealed + redrew → unchanged count
        assertEquals(1, s.influenceCount(P[1])) // challenger lost one
    }

    @Test
    fun challenge_bluff_costs_the_bluffer_and_voids_the_action() {
        val s0 = buildState(c2, listOf(listOf(Role.BHAI, Role.BABU), listOf(Role.VAKIL, Role.JUGAADU)), listOf(2, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.Tax)).ok() // bluff — no Neta
        s = applyIntent(s, Intent.Challenge(P[1])).ok()
        assertEquals(P[0], (s.phase as Phase.AwaitingInfluenceLoss).loser)
        s = applyIntent(s, legalIntents(s, P[0]).first()).ok()
        assertEquals(2, s.player(P[0]).coins) // no tax
        assertEquals(1, s.influenceCount(P[0]))
    }

    @Test
    fun supari_cost_is_paid_at_declaration_and_not_refunded_when_challenged_away() {
        val s0 = buildState(c2, listOf(listOf(Role.BABU, Role.VAKIL), listOf(Role.NETA, Role.JUGAADU)), listOf(3, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.Assassinate(P[1]))).ok() // bluff — no Bhai
        assertEquals(0, s.player(P[0]).coins) // E4: paid 3 at declaration
        s = applyIntent(s, Intent.Challenge(P[1])).ok()
        s = applyIntent(s, legalIntents(s, P[0]).first()).ok()
        assertEquals(0, s.player(P[0]).coins) // E4: NOT refunded
        assertEquals(2, s.influenceCount(P[1])) // target untouched
        assertEquals(1, s.influenceCount(P[0]))
    }

    @Test
    fun vasooli_against_zero_coin_target_is_illegal() {
        val s0 = buildState(c2, listOf(listOf(Role.BABU, Role.VAKIL), listOf(Role.NETA, Role.JUGAADU)), listOf(2, 0))
        assertFalse(legalActions(s0, P[0]).any { it is Action.Steal })
        val withCoin = buildState(c2, listOf(listOf(Role.BABU, Role.VAKIL), listOf(Role.NETA, Role.JUGAADU)), listOf(2, 1))
        assertTrue(legalActions(withCoin, P[0]).any { it is Action.Steal })
    }

    @Test
    fun forced_khela_at_ten_coins() {
        val s0 = buildState(c2, listOf(listOf(Role.NETA, Role.BHAI), listOf(Role.VAKIL, Role.BABU)), listOf(10, 2))
        val acts = legalActions(s0, P[0])
        assertTrue(acts.isNotEmpty() && acts.all { it is Action.Coup })
    }

    @Test
    fun khela_eliminates_last_influence_and_wins() {
        val s0 = buildState(c2, listOf(listOf(Role.NETA, Role.BHAI), listOf(Role.VAKIL)), listOf(7, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.Coup(P[1]))).ok()
        assertEquals(0, s.player(P[0]).coins) // paid 7
        assertTrue(s.phase is Phase.AwaitingInfluenceLoss)
        s = applyIntent(s, legalIntents(s, P[1]).first()).ok()
        assertTrue(s.phase is Phase.GameOver)
        assertEquals(P[0], (s.phase as Phase.GameOver).winner)
    }

    @Test
    fun deadliest_trap_bluffed_vakil_block_causes_double_loss() {
        // Actor holds Bhai (assassinate is truthful); target bluffs a Vakil block.
        val s0 = buildState(c2, listOf(listOf(Role.BHAI, Role.NETA), listOf(Role.NETA, Role.JUGAADU)), listOf(3, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.Assassinate(P[1]))).ok()
        s = applyIntent(s, Intent.Pass(P[1])).ok() // doesn't challenge the assassin
        s = applyIntent(s, Intent.Block(P[1], Role.VAKIL)).ok() // bluff block
        s = applyIntent(s, Intent.Challenge(P[0])).ok() // actor challenges the bluff
        s = applyIntent(s, legalIntents(s, P[1]).first()).ok() // 1st loss (failed block-challenge)
        assertTrue(s.phase is Phase.AwaitingInfluenceLoss) // assassination now lands → 2nd loss pending
        s = applyIntent(s, legalIntents(s, P[1]).first()).ok() // 2nd loss → eliminated
        assertTrue(s.phase is Phase.GameOver)
        assertEquals(P[0], (s.phase as Phase.GameOver).winner)
    }

    @Test
    fun target_who_challenges_a_real_assassin_can_still_lose_both() {
        // E3: 2-influence target challenges a real assassin, loses the challenge; the action survives, so a
        // block window re-opens (canonical Coup). The target declines to block → the hit lands → second loss.
        val s0 = buildState(c2, listOf(listOf(Role.BHAI, Role.NETA), listOf(Role.NETA, Role.JUGAADU)), listOf(3, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.Assassinate(P[1]))).ok()
        s = applyIntent(s, Intent.Challenge(P[1])).ok() // wrongly challenges the real Bhai
        s = applyIntent(s, legalIntents(s, P[1]).first()).ok() // 1st loss (failed challenge)
        assertTrue(s.phase is Phase.AwaitingReactions) // action survived → block window re-opens for the target
        s = applyIntent(s, Intent.Pass(P[1])).ok() // declines to block → assassination proceeds
        assertTrue(s.phase is Phase.AwaitingInfluenceLoss) // → second loss
        s = applyIntent(s, legalIntents(s, P[1]).first()).ok()
        assertTrue(s.phase is Phase.GameOver)
        assertEquals(P[0], (s.phase as Phase.GameOver).winner)
    }

    @Test
    fun out_of_turn_declaration_is_rejected() {
        val s0 = buildState(c2, listOf(listOf(Role.NETA, Role.BHAI), listOf(Role.VAKIL, Role.BABU)), listOf(2, 2))
        assertTrue(applyIntent(s0, Intent.DeclareAction(P[1], Action.Income)) is ApplyOutcome.Rejected)
    }

    @Test
    fun coup_below_cost_is_rejected() {
        val s0 = buildState(c2, listOf(listOf(Role.NETA, Role.BHAI), listOf(Role.VAKIL, Role.BABU)), listOf(6, 2))
        assertTrue(applyIntent(s0, Intent.DeclareAction(P[0], Action.Coup(P[1]))) is ApplyOutcome.Rejected)
    }

    @Test
    fun setting_exchange_keeps_influence_count() {
        val s0 =
            buildState(
                cfg(3),
                listOf(
                    listOf(Role.JUGAADU, Role.NETA),
                    listOf(Role.BHAI, Role.BABU),
                    listOf(Role.VAKIL, Role.NETA),
                ),
                listOf(2, 2, 2),
            )
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.Exchange)).ok()
        s = applyIntent(s, Intent.Pass(P[1])).ok()
        s = applyIntent(s, Intent.Pass(P[2])).ok()
        assertTrue(s.phase is Phase.AwaitingExchange)
        val keep = legalIntents(s, P[0]).first()
        s = applyIntent(s, keep).ok()
        assertEquals(2, s.influenceCount(P[0])) // exchange never changes how much influence you hold
        checkInvariants(s)
    }
}
