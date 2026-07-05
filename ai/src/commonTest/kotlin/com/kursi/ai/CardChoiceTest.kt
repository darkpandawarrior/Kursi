package com.kursi.ai

import com.kursi.engine.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the redaction-unlocked decisions: role-optimal Exchange keep-sets and
 * lowest-value influence-loss shedding, exercised through MediumPolicy and HardPolicy.
 *
 * These construct a [PlayerView] in the relevant phase directly (with [PlayerView.myCards] and
 * [PhaseView.Exchange.drawn] populated exactly as the engine's [redact] would for the acting viewer)
 * plus the matching legal intent list, then assert the chosen intent is role-optimal — NOT just
 * legal.first(), the prior placeholder behaviour.
 */
class CardChoiceTest {
    private val cfg = GameConfig.forPlayers(2)
    private val me = PlayerId(0)
    private val opp = PlayerId(1)

    private fun ownCard(
        id: Int,
        role: Role,
        faceUp: Boolean = false,
    ) = OwnCard(CardId(id), role, faceUp)

    /** Build an Exchange-phase view + the legal ChooseExchange intents exactly as the engine would. */
    private fun exchangeView(
        ownFaceDown: List<Pair<Int, Role>>,
        drawn: List<Pair<Int, Role>>,
    ): Pair<PlayerView, List<Intent>> {
        val ownCards = ownFaceDown.map { ownCard(it.first, it.second) }
        val drawnCards = drawn.map { ownCard(it.first, it.second) }
        val pool = ownCards.map { it.id } + drawnCards.map { it.id }
        val keepSize = ownCards.size
        val legal = combinations(pool, keepSize).map { Intent.ChooseExchange(me, it) }
        val view =
            PlayerView(
                viewer = me,
                config = cfg,
                treasury = 10,
                deckCount = 1,
                turnNumber = 5,
                myCoins = 2,
                myInfluence = ownCards.map { it.role }.sortedBy { it.ordinal },
                myFaceUp = emptyList(),
                myCards = ownCards,
                players =
                    listOf(
                        OpponentView(me, 0, 2, emptyList(), keepSize, false),
                        OpponentView(opp, 1, 2, emptyList(), 2, false),
                    ),
                phase = PhaseView.Exchange(me, drawnCards),
            )
        return view to legal
    }

    /** Build an InfluenceLoss-phase view + the legal ChooseInfluenceToLose intents (one per face-down card). */
    private fun lossView(ownFaceDown: List<Pair<Int, Role>>): Pair<PlayerView, List<Intent>> {
        val ownCards = ownFaceDown.map { ownCard(it.first, it.second) }
        val legal = ownCards.map { Intent.ChooseInfluenceToLose(me, it.id) }
        val view =
            PlayerView(
                viewer = me,
                config = cfg,
                treasury = 10,
                deckCount = 1,
                turnNumber = 5,
                myCoins = 2,
                myInfluence = ownCards.map { it.role }.sortedBy { it.ordinal },
                myFaceUp = emptyList(),
                myCards = ownCards,
                players =
                    listOf(
                        OpponentView(me, 0, 2, emptyList(), ownCards.size, false),
                        OpponentView(opp, 1, 2, emptyList(), 2, false),
                    ),
                phase = PhaseView.InfluenceLoss(me, LossReason.COUPED),
            )
        return view to legal
    }

    private fun keptRoles(
        view: PlayerView,
        intent: Intent,
    ): Set<Role> {
        val roleOf = CardChoice.exchangeRoleOf(view)
        return (intent as Intent.ChooseExchange).keep.map { roleOf.getValue(it) }.toSet()
    }

    // ── Exchange: role-optimal, and NOT always legal.first() ────────────────────

    @Test
    fun exchange_keepsHighestValueRoles_notFirst() {
        // Own face-down: JUGAADU(1), VAKIL(2) — both low value.
        // Drawn: NETA(5), BABU(4) — both high value. Keeping the drawn pair is strictly best.
        val (view, legal) =
            exchangeView(
                ownFaceDown = listOf(0 to Role.JUGAADU, 1 to Role.VAKIL),
                drawn = listOf(2 to Role.NETA, 3 to Role.BABU),
            )
        val first = legal.first()

        for (policy in listOf(MediumPolicy(1L), HardPolicy(1L))) {
            val chosen = policy.decide(view, legal)
            assertTrue(chosen in legal, "chosen must be legal")
            // Optimal keep is exactly {NETA, BABU} — the drawn pair beats the originals.
            assertEquals(
                setOf(Role.NETA, Role.BABU),
                keptRoles(view, chosen),
                "${policy::class.simpleName} should keep the drawn high-value pair",
            )
            // And it must NOT be the placeholder legal.first() (which keeps the two originals).
            assertTrue(
                chosen != first || keptRoles(view, first) == setOf(Role.NETA, Role.BABU),
                "${policy::class.simpleName} must not blindly return legal.first()",
            )
            assertEquals(
                setOf(Role.JUGAADU, Role.VAKIL),
                keptRoles(view, first),
                "sanity: legal.first() keeps the originals, so a correct policy diverges from it",
            )
        }
    }

    @Test
    fun exchange_keepsBestSplit_mixedPool() {
        // Own: NETA(5), JUGAADU(1). Drawn: BABU(4), BHAI(3).
        // Best keep-2 from {NETA,JUGAADU,BABU,BHAI} = {NETA, BABU} (value 9).
        val (view, legal) =
            exchangeView(
                ownFaceDown = listOf(0 to Role.NETA, 1 to Role.JUGAADU),
                drawn = listOf(2 to Role.BABU, 3 to Role.BHAI),
            )
        for (policy in listOf(MediumPolicy(7L), HardPolicy(7L))) {
            val chosen = policy.decide(view, legal)
            assertEquals(
                setOf(Role.NETA, Role.BABU),
                keptRoles(view, chosen),
                "${policy::class.simpleName} should keep the optimal NETA+BABU split",
            )
        }
    }

    // ── InfluenceLoss: sheds the intended lowest-value role ─────────────────────

    @Test
    fun loss_shedsLowestValueRole() {
        // Face-down: NETA(5) at CardId 0, JUGAADU(1) at CardId 1.
        // myInfluence sorted by ordinal is [NETA, JUGAADU] (NETA ordinal 0 < JUGAADU ordinal 3),
        // so a naive positional map would shed index 0 = NETA (the BEST card) — the historical bug.
        // Correct behaviour: shed JUGAADU (CardId 1).
        val (view, legal) = lossView(listOf(0 to Role.NETA, 1 to Role.JUGAADU))
        for (policy in listOf(MediumPolicy(3L), HardPolicy(3L))) {
            val chosen = policy.decide(view, legal) as Intent.ChooseInfluenceToLose
            assertEquals(
                CardId(1),
                chosen.card,
                "${policy::class.simpleName} should shed JUGAADU (lowest value), not NETA",
            )
        }
    }

    @Test
    fun loss_shedsLowestValueRole_reversedCardOrder() {
        // Same roles but CardIds swapped: JUGAADU at 0, NETA at 1.
        // Confirms the choice tracks the ACTUAL role via myCards, not the CardId/ordinal position.
        val (view, legal) = lossView(listOf(0 to Role.JUGAADU, 1 to Role.NETA))
        for (policy in listOf(MediumPolicy(9L), HardPolicy(9L))) {
            val chosen = policy.decide(view, legal) as Intent.ChooseInfluenceToLose
            assertEquals(
                CardId(0),
                chosen.card,
                "${policy::class.simpleName} should shed JUGAADU at CardId 0",
            )
        }
    }

    @Test
    fun loss_keepsHigherOfTwoMidRoles() {
        // BABU(4) vs BHAI(3): shed BHAI.
        val (view, legal) = lossView(listOf(5 to Role.BABU, 8 to Role.BHAI))
        val chosen = HardPolicy(2L).decide(view, legal) as Intent.ChooseInfluenceToLose
        assertEquals(CardId(8), chosen.card, "should shed BHAI, keep BABU")
    }
}
