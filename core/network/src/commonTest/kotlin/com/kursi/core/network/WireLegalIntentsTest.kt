package com.kursi.core.network

import com.kursi.protocol.wire.WireAction
import com.kursi.protocol.wire.WireGameConfig
import com.kursi.protocol.wire.WireIntent
import com.kursi.protocol.wire.WireOpponentView
import com.kursi.protocol.wire.WireOwnCard
import com.kursi.protocol.wire.WirePhaseView
import com.kursi.protocol.wire.WirePlayerView
import com.kursi.protocol.wire.WireReactionStep
import com.kursi.protocol.wire.WireRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-function tests for the client-side legal-intent deriver. These prove a real client can build
 * the correct move-set from ONLY its redacted [WirePlayerView] — the same projection the UI relies on.
 */
class WireLegalIntentsTest {
    private fun config(roleCount: Int = 5) =
        WireGameConfig(
            seatCount = 2,
            copiesPerRole = 3,
            roleCount = roleCount,
            influencePerPlayer = 2,
            startingCoins = 2,
            coupCost = 7,
            assassinateCost = 3,
            taxAmount = 3,
            foreignAidAmount = 2,
            stealAmount = 2,
            incomeAmount = 1,
            exchangeDrawCount = 2,
            forcedCoupThreshold = 10,
            coinSupply = 50,
        )

    private fun opp(
        id: Int,
        coins: Int = 2,
        faceDown: Int = 2,
        eliminated: Boolean = false,
    ) = WireOpponentView(
        id = id,
        seatIndex = id,
        coins = coins,
        faceUpRoles = emptyList(),
        faceDownCount = faceDown,
        eliminated = eliminated,
    )

    private fun turnView(
        viewer: Int = 0,
        myCoins: Int = 2,
        actor: Int = 0,
        roleCount: Int = 5,
        opponentCoins: Int = 2,
        opponentFaceDown: Int = 2,
    ) = WirePlayerView(
        viewer = viewer,
        config = config(roleCount),
        treasury = 30,
        deckCount = 10,
        turnNumber = 1,
        myCoins = myCoins,
        myInfluence = listOf(WireRole.NETA, WireRole.BHAI),
        myFaceUp = emptyList(),
        myCards =
            listOf(
                WireOwnCard(0, WireRole.NETA, faceUp = false),
                WireOwnCard(1, WireRole.BHAI, faceUp = false),
            ),
        players = listOf(opp(viewer, myCoins), opp(1 - viewer, opponentCoins, opponentFaceDown)),
        phase = WirePhaseView.Turn(actor = actor),
    )

    @Test
    fun `not my turn yields no legal intents`() {
        val view = turnView(viewer = 0, actor = 1)
        assertTrue(view.legalIntents().isEmpty())
        assertFalse(view.isMyTurn())
    }

    @Test
    fun `basic turn offers income foreignAid tax exchange and steal`() {
        val view = turnView(viewer = 0, myCoins = 2, actor = 0, opponentCoins = 2)
        val actions = view.legalIntents().filterIsInstance<WireIntent.DeclareAction>().map { it.action }
        assertTrue(WireAction.Income in actions)
        assertTrue(WireAction.ForeignAid in actions)
        assertTrue(WireAction.Tax in actions)
        assertTrue(WireAction.Exchange in actions)
        assertTrue(actions.any { it is WireAction.Steal }, "steal on a 1+ coin opponent must be legal")
        // No coup/assassinate at 2 coins.
        assertFalse(actions.any { it is WireAction.Coup })
        assertFalse(actions.any { it is WireAction.Assassinate })
        assertTrue(view.isMyTurn())
    }

    @Test
    fun `steal is illegal against a zero-coin opponent`() {
        val view = turnView(viewer = 0, actor = 0, opponentCoins = 0)
        val actions = view.legalIntents().filterIsInstance<WireIntent.DeclareAction>().map { it.action }
        assertFalse(actions.any { it is WireAction.Steal }, "D9: cannot steal from a 0-coin target")
    }

    @Test
    fun `forced coup at threshold is the only action`() {
        val view = turnView(viewer = 0, myCoins = 10, actor = 0)
        val actions = view.legalIntents().filterIsInstance<WireIntent.DeclareAction>().map { it.action }
        assertTrue(actions.all { it is WireAction.Coup }, "at >=10 coins, only Coup is legal; got $actions")
        assertTrue(actions.isNotEmpty())
    }

    @Test
    fun `investigate offered only when the sixth role is in the deck`() {
        val without =
            turnView(viewer = 0, actor = 0, roleCount = 5)
                .legalIntents()
                .filterIsInstance<WireIntent.DeclareAction>()
                .map { it.action }
        assertFalse(without.any { it is WireAction.Investigate }, "no PATRAKAAR in a 5-role deck → no Jaanch")

        val with =
            turnView(viewer = 0, actor = 0, roleCount = 6, opponentFaceDown = 2)
                .legalIntents()
                .filterIsInstance<WireIntent.DeclareAction>()
                .map { it.action }
        assertTrue(with.any { it is WireAction.Investigate }, "6-role deck → Jaanch offered against a face-down target")
    }

    @Test
    fun `reactions challenge step offers challenge and pass`() {
        val view =
            turnView(viewer = 1).copy(
                phase =
                    WirePhaseView.Reactions(
                        actor = 0,
                        action = WireAction.Tax,
                        claimedRole = WireRole.JUGAADU,
                        step = WireReactionStep.CHALLENGE_ACTION,
                        toRespond = 1,
                        blocker = null,
                        blockRole = null,
                    ),
            )
        val intents = view.legalIntents()
        assertTrue(intents.any { it is WireIntent.Challenge })
        assertTrue(intents.any { it is WireIntent.Pass })
        assertTrue(view.isMyTurn())
    }

    @Test
    fun `block step offers pass plus the roles that block the action`() {
        // ForeignAid is blocked by NETA.
        val view =
            turnView(viewer = 1).copy(
                phase =
                    WirePhaseView.Reactions(
                        actor = 0,
                        action = WireAction.ForeignAid,
                        claimedRole = null,
                        step = WireReactionStep.BLOCK,
                        toRespond = 1,
                        blocker = null,
                        blockRole = null,
                    ),
            )
        val intents = view.legalIntents()
        assertTrue(intents.any { it is WireIntent.Pass })
        val blockRoles = intents.filterIsInstance<WireIntent.Block>().map { it.role }
        assertEquals(listOf(WireRole.NETA), blockRoles, "only NETA blocks ForeignAid")
    }

    @Test
    fun `influence loss enumerates own face-down cards by id`() {
        val view =
            turnView(viewer = 0).copy(
                phase = WirePhaseView.InfluenceLoss(loser = 0, reason = com.kursi.protocol.wire.WireLossReason.COUPED),
            )
        val choices = view.legalIntents().filterIsInstance<WireIntent.ChooseInfluenceToLose>().map { it.card }
        assertEquals(setOf(0, 1), choices.toSet(), "must offer each own face-down CardId exactly once")
    }

    @Test
    fun `exchange enumerates keep-combinations from own plus drawn cards`() {
        // Own 2 face-down (ids 0,1) + 2 drawn (ids 5,6); keep 2 → C(4,2) = 6 combinations.
        val view =
            turnView(viewer = 0).copy(
                phase =
                    WirePhaseView.Exchange(
                        actor = 0,
                        drawn = listOf(WireOwnCard(5, WireRole.VAKIL, false), WireOwnCard(6, WireRole.BABU, false)),
                    ),
            )
        val keeps = view.legalIntents().filterIsInstance<WireIntent.ChooseExchange>().map { it.keep.toSet() }
        assertEquals(6, keeps.size, "C(4,2) keep-combinations")
        assertTrue(keeps.all { it.size == 2 })
        assertTrue(setOf(0, 1) in keeps && setOf(5, 6) in keeps)
    }

    @Test
    fun `investigate peek offers resolve with and without redraw for the examiner only`() {
        val examinerView =
            turnView(viewer = 0).copy(
                phase = WirePhaseView.InvestigatePeek(examiner = 0, target = 1),
            )
        val forced = examinerView.legalIntents().filterIsInstance<WireIntent.ResolveInvestigate>().map { it.forceRedraw }
        assertEquals(setOf(false, true), forced.toSet())

        val targetView =
            turnView(viewer = 1).copy(
                phase = WirePhaseView.InvestigatePeek(examiner = 0, target = 1),
            )
        assertTrue(targetView.legalIntents().isEmpty(), "the examined target has no input here")
    }
}
