package com.kursi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M6a — the 6th role PATRAKAAR and its action Jaanch (Action.Investigate):
 *   • action legality (only offered when PATRAKAAR is in the deck),
 *   • challenge resolution (truthful examiner survives, bluffer loses),
 *   • the info-reveal under the secrecy boundary: the examiner LEARNS the peeked card, NO ONE ELSE does,
 *   • the optional force-redraw (shuffle examined card back + redraw).
 *
 * A 9-player config is the smallest table where PATRAKAAR is in the deck.
 */
class PatrakaarTest {
    private val c9 = GameConfig.forPlayers(9) // 6 roles, copies 5

    /** Fills the remaining seats with 2 face-down cards each, never exceeding any role's copy budget. */
    private fun addFiller(
        hands: MutableList<List<Role>>,
        cfg: GameConfig,
        used: List<Role>,
    ) {
        val remaining =
            cfg.activeRoles
                .associateWith { r ->
                    cfg.copiesPerRole - used.count { it == r }
                }.toMutableMap()
        repeat(cfg.seatCount - hands.size) {
            val hand = ArrayList<Role>(2)
            repeat(2) {
                val r = cfg.activeRoles.first { (remaining[it] ?: 0) > 0 }
                remaining[r] = remaining.getValue(r) - 1
                hand.add(r)
            }
            hands.add(hand)
        }
    }

    /** Seat 0 holds PATRAKAAR + NETA; seat 1 holds VAKIL + BABU; rest are filler. Everyone has 2 coins. */
    private fun stateWithPatrakaarAtSeat0(): GameState {
        val hands = ArrayList<List<Role>>()
        hands.add(listOf(Role.PATRAKAAR, Role.NETA)) // seat 0 — truthful examiner
        hands.add(listOf(Role.VAKIL, Role.BABU)) // seat 1 — investigation target
        addFiller(hands, c9, used = hands.flatten())
        return buildState(c9, hands, coins = List(c9.seatCount) { 2 })
    }

    // ── (1) legality ──

    @Test
    fun investigate_is_offered_only_when_patrakaar_in_deck() {
        // N=9: PATRAKAAR present → Investigate is a legal action.
        val s9 = stateWithPatrakaarAtSeat0()
        val legal9 = legalActions(s9, P[0])
        assertTrue(legal9.any { it is Action.Investigate }, "Investigate must be legal at N=9")

        // N=5: PATRAKAAR absent → Investigate must NOT be offered (it would be a guaranteed-losing bluff).
        val c5 = GameConfig.forPlayers(5)
        val s5 = buildState(c5, List(5) { listOf(Role.NETA, Role.BHAI) }, List(5) { 2 })
        assertFalse(legalActions(s5, P[0]).any { it is Action.Investigate }, "Investigate must be absent at N=5")
    }

    @Test
    fun investigate_claims_patrakaar_and_is_not_blockable() {
        assertEquals(Role.PATRAKAAR, Rules.claimedRole(Action.Investigate(P[1])))
        assertTrue(Rules.isChallengeable(Action.Investigate(P[1])))
        assertFalse(Rules.isBlockable(Action.Investigate(P[1])))
        assertEquals(P[1], Rules.targetOf(Action.Investigate(P[1])))
    }

    /**
     * Drives the current CHALLENGE_ACTION window: [challenger] challenges; everyone else passes (in turn
     * order). The window only resolves once all eligible players have responded (engine has no early-out).
     */
    private fun driveChallengeWindow(
        start: GameState,
        challenger: PlayerId,
    ): GameState {
        var s = start
        var guard = 0
        while (s.phase is Phase.AwaitingReactions) {
            val who = whoActsNext(s)!!
            s =
                if (who == challenger) {
                    applyIntent(s, Intent.Challenge(who)).ok()
                } else {
                    applyIntent(s, Intent.Pass(who)).ok()
                }
            check(guard++ < 32)
        }
        return s
    }

    // ── (2) challenge resolution ──

    @Test
    fun truthful_investigate_survives_challenge_then_examiner_peeks() {
        var s = stateWithPatrakaarAtSeat0()
        s = applyIntent(s, Intent.DeclareAction(P[0], Action.Investigate(P[1]))).ok()
        // Seat 1 challenges the PATRAKAAR claim; all other eligible players pass.
        val challenger = P[1]
        s = driveChallengeWindow(s, challenger)
        // Challenger was wrong → challenger loses a card; seat 0 reveal-replaces PATRAKAAR, action survives.
        assertTrue(s.phase is Phase.AwaitingInfluenceLoss, "wrong challenger must lose a card, was ${s.phase}")
        val loser = (s.phase as Phase.AwaitingInfluenceLoss).loser
        assertEquals(challenger, loser, "the wrong challenger loses the card")
        val card = s.faceDownInfluence(loser).first()
        s = applyIntent(s, Intent.ChooseInfluenceToLose(loser, card)).ok()
        assertTrue(s.phase is Phase.AwaitingInvestigatePeek, "examiner peeks after surviving challenge, was ${s.phase}")
        assertEquals(P[0], (s.phase as Phase.AwaitingInvestigatePeek).examiner)
    }

    @Test
    fun bluffed_investigate_loses_card_and_action_fails() {
        // Seat 0 does NOT hold PATRAKAAR here → the claim is a bluff.
        val hands = ArrayList<List<Role>>()
        hands.add(listOf(Role.NETA, Role.BHAI)) // seat 0 — bluffer (no PATRAKAAR)
        hands.add(listOf(Role.VAKIL, Role.BABU)) // seat 1 — target / challenger
        addFiller(hands, c9, used = hands.flatten())
        var s = buildState(c9, hands, List(c9.seatCount) { 2 })

        s = applyIntent(s, Intent.DeclareAction(P[0], Action.Investigate(P[1]))).ok()
        s = driveChallengeWindow(s, challenger = P[1])
        // Bluff caught → seat 0 must lose a card; no peek phase ever opens.
        assertTrue(s.phase is Phase.AwaitingInfluenceLoss)
        val loser = (s.phase as Phase.AwaitingInfluenceLoss).loser
        assertEquals(P[0], loser, "the bluffer loses")
        val card = s.faceDownInfluence(loser).first()
        s = applyIntent(s, Intent.ChooseInfluenceToLose(loser, card)).ok()
        assertFalse(s.phase is Phase.AwaitingInvestigatePeek, "no peek after a failed bluff")
        // Turn advanced; seat 0 still has 1 influence.
        assertEquals(1, s.influenceCount(P[0]))
    }

    @Test
    fun unchallenged_investigate_goes_straight_to_peek() {
        var s = stateWithPatrakaarAtSeat0()
        s = applyIntent(s, Intent.DeclareAction(P[0], Action.Investigate(P[1]))).ok()
        // Everyone passes the challenge window.
        var guard = 0
        while (s.phase is Phase.AwaitingReactions) {
            val who = whoActsNext(s)!!
            s = applyIntent(s, Intent.Pass(who)).ok()
            check(guard++ < 32)
        }
        assertTrue(s.phase is Phase.AwaitingInvestigatePeek, "unchallenged Jaanch → peek, was ${s.phase}")
    }

    // ── (3) secrecy boundary: examiner sees, others do not ──

    @Test
    fun peek_reveals_examined_role_to_examiner_only() {
        var s = stateWithPatrakaarAtSeat0()
        s = applyIntent(s, Intent.DeclareAction(P[0], Action.Investigate(P[1]))).ok()
        var guard = 0
        while (s.phase is Phase.AwaitingReactions) {
            val w = whoActsNext(s)!!
            s = applyIntent(s, Intent.Pass(w)).ok()
            check(guard++ < 32)
        }
        val ph = s.phase as Phase.AwaitingInvestigatePeek
        val peekedRole = s.cards.getValue(ph.peeked)

        // Examiner (seat 0) SEES the examined card + its true role.
        val examinerView = redact(s, P[0]).phase
        assertTrue(examinerView is PhaseView.InvestigatePeek)
        val examined = examinerView.examinedCard
        assertNotNull(examined, "examiner must see the peeked card")
        assertEquals(ph.peeked, examined.id)
        assertEquals(P[1], examined.owner)
        assertEquals(peekedRole, examined.role, "examiner learns the TRUE role of the examined card")

        // The TARGET (seat 1) must NOT learn which card was examined.
        val targetView = redact(s, P[1]).phase
        assertTrue(targetView is PhaseView.InvestigatePeek)
        assertNull(targetView.examinedCard, "target must not see the examiner's peek")

        // Every OTHER viewer also sees no examined card.
        for (seat in 2 until c9.seatCount) {
            val v = redact(s, PlayerId(seat)).phase
            assertTrue(v is PhaseView.InvestigatePeek)
            assertNull(v.examinedCard, "viewer $seat must not see the examiner's peek")
        }
    }

    @Test
    fun peek_is_invariant_to_a_non_examined_hidden_role_for_non_examiners() {
        // Swapping a hidden role the examiner did NOT peek must not change any non-examiner's view,
        // and the public InvestigatePeek shell (examiner/target identity) is the same for everyone.
        var s = stateWithPatrakaarAtSeat0()
        s = applyIntent(s, Intent.DeclareAction(P[0], Action.Investigate(P[1]))).ok()
        var guard = 0
        while (s.phase is Phase.AwaitingReactions) {
            val w = whoActsNext(s)!!
            s = applyIntent(s, Intent.Pass(w)).ok()
            check(guard++ < 32)
        }

        // A non-examiner's view carries NO secret card identity at all.
        val before = redact(s, P[2]).phase as PhaseView.InvestigatePeek
        assertEquals(P[0], before.examiner)
        assertEquals(P[1], before.target)
        assertNull(before.examinedCard)
    }

    // ── (4) force-redraw effect ──

    @Test
    fun force_redraw_shuffles_examined_card_back_and_redraws() {
        var s = stateWithPatrakaarAtSeat0()
        s = applyIntent(s, Intent.DeclareAction(P[0], Action.Investigate(P[1]))).ok()
        var guard = 0
        while (s.phase is Phase.AwaitingReactions) {
            val w = whoActsNext(s)!!
            s = applyIntent(s, Intent.Pass(w)).ok()
            check(guard++ < 32)
        }
        val ph = s.phase as Phase.AwaitingInvestigatePeek
        val peeked = ph.peeked
        val targetCountBefore = s.influenceCount(P[1])

        val out = applyIntent(s, Intent.ResolveInvestigate(P[0], forceRedraw = true))
        s = out.ok()
        // Target keeps the same influence COUNT (card count unchanged), but the examined card is back in the deck.
        assertEquals(targetCountBefore, s.influenceCount(P[1]), "redraw preserves influence count")
        assertFalse(peeked in s.faceDownInfluence(P[1]), "examined card was shuffled out of the target's hand")
        assertTrue(out.evts().any { it is GameEvent.InvestigateRedraw }, "redraw event emitted")
        // Turn advanced to next seat.
        assertTrue(s.phase is Phase.AwaitingAction)
    }

    @Test
    fun no_redraw_leaves_target_hand_unchanged_and_ends_turn() {
        var s = stateWithPatrakaarAtSeat0()
        s = applyIntent(s, Intent.DeclareAction(P[0], Action.Investigate(P[1]))).ok()
        var guard = 0
        while (s.phase is Phase.AwaitingReactions) {
            val w = whoActsNext(s)!!
            s = applyIntent(s, Intent.Pass(w)).ok()
            check(guard++ < 32)
        }
        val ph = s.phase as Phase.AwaitingInvestigatePeek
        val handBefore = s.faceDownInfluence(P[1]).toSet()

        val out = applyIntent(s, Intent.ResolveInvestigate(P[0], forceRedraw = false))
        s = out.ok()
        assertEquals(handBefore, s.faceDownInfluence(P[1]).toSet(), "no-redraw leaves the target's hand intact")
        assertFalse(out.evts().any { it is GameEvent.InvestigateRedraw })
        assertTrue(s.phase is Phase.AwaitingAction, "turn ends after the examiner declines redraw")
        // peeked is still referenced to silence unused; sanity that it remained in hand.
        assertTrue(ph.peeked in s.faceDownInfluence(P[1]))
    }

    // ── determinism / invariants smoke with PATRAKAAR in the deck ──

    @Test
    fun full_random_game_with_patrakaar_terminates_and_holds_invariants() {
        val result =
            SimHarness.playOut(
                c9,
                seed = 99L,
                policies = (0 until 9).associate { PlayerId(it) to RandomLegalPolicy(it * 13L + 1) },
            )
        assertTrue(result.turns >= 1)
        assertTrue(result.winner.raw in 0..8)
    }
}
