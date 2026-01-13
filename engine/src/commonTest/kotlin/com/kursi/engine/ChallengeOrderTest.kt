package com.kursi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec §A.5 step 3 — within a proven challenge the mutation order is:
 *   remove challenger's penalty card → reshuffle prover's proven card → prover draws.
 * In event terms this pins InfluenceLost (challenger) BEFORE CardReplaced (prover), which matters
 * for UI animation timing and for double-loss/elimination sequencing (Edges #10, #12).
 *
 * Also pins the single canonical clockwise seat ordering (GameState.aliveFromLeftOf) used by every
 * reaction-window eligibility query.
 */
class ChallengeOrderTest {

    private val c2 = cfg(2)

    private fun List<GameEvent>.indexOfFirstOrFail(pred: (GameEvent) -> Boolean): Int {
        val idx = indexOfFirst(pred)
        assertTrue(idx >= 0, "expected event not found in $this")
        return idx
    }

    @Test
    fun proven_action_challenge_emits_influence_loss_before_card_replace() {
        // P0 truthfully claims Neta (Tax); P1 challenges and is wrong → P1 loses, P0 reveal-replaces.
        val s0 = buildState(c2, listOf(listOf(Role.NETA, Role.BHAI), listOf(Role.VAKIL, Role.BABU)), listOf(2, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.Tax)).ok()
        val chOut = applyIntent(s, Intent.Challenge(P[1])); s = chOut.ok()
        // P1 chooses which influence to lose; this step emits InfluenceLost (P1) then CardReplaced (P0).
        val lossOut = applyIntent(s, legalIntents(s, P[1]).first())
        val events = chOut.evts() + lossOut.evts()
        val lostIdx = events.indexOfFirstOrFail { it is GameEvent.InfluenceLost && it.player == P[1] }
        val replacedIdx = events.indexOfFirstOrFail { it is GameEvent.CardReplaced && it.player == P[0] }
        assertTrue(lostIdx < replacedIdx, "A.5: challenger InfluenceLost must precede prover CardReplaced; got $events")
        // Sanity: Tax still resolved and counts are preserved.
        assertEquals(5, lossOut.ok().player(P[0]).coins)
        assertEquals(2, lossOut.ok().influenceCount(P[0]))
    }

    @Test
    fun proven_block_challenge_emits_influence_loss_before_card_replace() {
        // P0 plays FDI; P1 blocks with a REAL Neta; P0 challenges and is wrong → P0 loses, P1 reveal-replaces.
        // (Edge #12 shape: proven block stands, challenger loses first, blocker reveal-replaces after.)
        val s0 = buildState(c2, listOf(listOf(Role.BHAI, Role.BABU), listOf(Role.NETA, Role.JUGAADU)), listOf(2, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.ForeignAid)).ok()
        s = applyIntent(s, Intent.Block(P[1], Role.NETA)).ok()
        val challengeOut = applyIntent(s, Intent.Challenge(P[0]))
        s = challengeOut.ok()
        // P0 (the challenger) now chooses the card to lose; that intent emits both InfluenceLost + CardReplaced.
        val lossOut = applyIntent(s, legalIntents(s, P[0]).first())
        val events = challengeOut.evts() + lossOut.evts()
        val lostIdx = events.indexOfFirstOrFail { it is GameEvent.InfluenceLost && it.player == P[0] }
        val replacedIdx = events.indexOfFirstOrFail { it is GameEvent.CardReplaced && it.player == P[1] }
        assertTrue(lostIdx < replacedIdx, "A.5: challenger InfluenceLost must precede blocker CardReplaced; got $events")
        // Block stood → FDI negated, P0 gained nothing.
        assertEquals(2, lossOut.ok().player(P[0]).coins)
    }

    @Test
    fun edge10_double_loss_keeps_per_loss_order_loss_then_replace_then_hit() {
        // Edge #10: 2-influence target wrongly challenges a REAL assassin.
        // Order must be: target's failed-challenge loss → assassin reveal-replaces Bhai → (block declined) → hit.
        val s0 = buildState(c2, listOf(listOf(Role.BHAI, Role.NETA), listOf(Role.NETA, Role.JUGAADU)), listOf(3, 2))
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.Assassinate(P[1]))).ok()
        val chOut = applyIntent(s, Intent.Challenge(P[1])); s = chOut.ok()
        val loss1Out = applyIntent(s, legalIntents(s, P[1]).first()); s = loss1Out.ok()
        val events = chOut.evts() + loss1Out.evts()
        val lostIdx = events.indexOfFirstOrFail { it is GameEvent.InfluenceLost && it.player == P[1] }
        val replacedIdx = events.indexOfFirstOrFail { it is GameEvent.CardReplaced && it.player == P[0] }
        assertTrue(lostIdx < replacedIdx, "Edge #10: failed-challenge loss precedes assassin reveal-replace; got $events")
        // Action survived → block window re-opens for the target; declining lands the hit (2nd loss).
        assertTrue(s.phase is Phase.AwaitingReactions)
        s = applyIntent(s, Intent.Pass(P[1])).ok()
        assertTrue(s.phase is Phase.AwaitingInfluenceLoss)
        s = applyIntent(s, legalIntents(s, P[1]).first()).ok()
        assertTrue(s.phase is Phase.GameOver)
        assertEquals(P[0], (s.phase as Phase.GameOver).winner)
    }

    // ── unified seat ordering ──

    @Test
    fun aliveFromLeftOf_is_clockwise_and_lands_start_seat_last() {
        val s = buildState(cfg(4), List(4) { listOf(Role.NETA, Role.BHAI) }, List(4) { 2 })
        // From seat 1, clockwise: 2,3,0,1 — start seat (1) wraps to the end.
        assertEquals(listOf(P[2], P[3], P[0], P[1]), s.aliveFromLeftOf(1))
        // Excluding the start-seat occupant gives the strict "everyone but me, clockwise" order.
        assertEquals(listOf(P[2], P[3], P[0]), s.aliveFromLeftOf(1, setOf(P[1])))
    }

    @Test
    fun aliveFromLeftOf_skips_eliminated_players() {
        // Seat 2 is eliminated (no face-down influence: give it a single face-up card via 0 face-down).
        val s = buildState(
            cfg(4),
            listOf(listOf(Role.NETA, Role.BHAI), listOf(Role.VAKIL, Role.BABU), emptyList(), listOf(Role.JUGAADU, Role.NETA)),
            List(4) { 2 },
        )
        assertTrue(!s.isAlive(P[2]))
        // From seat 0 clockwise: 1,2,3,0; drop eliminated P2 and the excluded start occupant P0 → [P1, P3].
        assertEquals(listOf(P[1], P[3]), s.aliveFromLeftOf(0, setOf(P[0])))
    }

    @Test
    fun challenge_block_window_includes_the_actor_in_clockwise_order() {
        // 3 players. P0 plays FDI, P1 blocks (Neta). The challenge-block window is everyone-but-blocker,
        // clockwise from the actor: seats (1,2,0) minus P1 → [P2, P0]. The ACTOR (P0) is eligible and last.
        val s0 = buildState(
            cfg(3),
            listOf(listOf(Role.BHAI, Role.BABU), listOf(Role.NETA, Role.JUGAADU), listOf(Role.VAKIL, Role.NETA)),
            listOf(2, 2, 2),
        )
        var s = applyIntent(s0, Intent.DeclareAction(P[0], Action.ForeignAid)).ok()
        // FDI block window is all opponents [P1, P2]; both must respond before the window resolves.
        s = applyIntent(s, Intent.Block(P[1], Role.NETA)).ok()
        s = applyIntent(s, Intent.Pass(P[2])).ok()
        assertTrue(s.phase is Phase.AwaitingReactions)
        assertEquals(ReactionStep.CHALLENGE_BLOCK, (s.phase as Phase.AwaitingReactions).ctx.step)
        // whoActsNext walks the eligible list in order: P2 first…
        assertEquals(P[2], whoActsNext(s))
        s = applyIntent(s, Intent.Pass(P[2])).ok()
        // …then the actor P0, who is permitted to challenge their own action's block.
        assertEquals(P[0], whoActsNext(s))
    }
}
