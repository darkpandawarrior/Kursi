package com.kursi.feature.game

import com.kursi.ai.advisor.MoveAdvice
import com.kursi.engine.Action
import com.kursi.engine.Intent
import com.kursi.engine.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M6b — decision-quality grading: turning the decision-coach's ranked [MoveAdvice] + the human's
 * chosen [Intent] into a per-decision [DecisionQuality], and folding those into a [MatchDecisionTally].
 */
class DecisionQualityTest {

    private fun advice(
        intent: Intent,
        winProb: Double,
        recommended: Boolean = false,
        bluff: Boolean = false,
        truthful: Boolean? = null,
        successOdds: Double? = null,
    ) = MoveAdvice(
        intent = intent,
        label = intent.toString(),
        winProb = winProb,
        recommended = recommended,
        truthful = truthful,
        bluff = bluff,
        successOdds = successOdds,
        rationale = "",
    )

    private val me = PlayerId(0)
    private val income = Intent.DeclareAction(me, Action.Income)
    private val tax = Intent.DeclareAction(me, Action.Tax)
    private val challenge = Intent.Challenge(me)
    private val pass = Intent.Pass(me)

    @Test
    fun playingTheBestMove_matchesWithZeroEvLost() {
        val list = listOf(
            advice(tax, winProb = 0.62, recommended = true),
            advice(income, winProb = 0.50),
        )
        val q = DecisionQuality.grade(tax, list)!!
        assertTrue(q.matchedBest)
        assertEquals(0L, q.evLostMilli)
        assertFalse(q.wasChallenge)
        assertFalse(q.wasBluff)
    }

    @Test
    fun playingASubMove_doesNotMatchAndBleedsEv() {
        val list = listOf(
            advice(tax, winProb = 0.62, recommended = true),
            advice(income, winProb = 0.50),
        )
        val q = DecisionQuality.grade(income, list)!!
        assertFalse(q.matchedBest)
        // 0.62 − 0.50 = 0.12 → 120 milli.
        assertEquals(120L, q.evLostMilli)
    }

    @Test
    fun challenge_isGradedGoodWhenOddsFavourBluff() {
        // successOdds = P(opponent bluffing). ≥ 0.5 → +EV challenge.
        val good = listOf(
            advice(challenge, winProb = 0.55, recommended = true, successOdds = 0.7),
            advice(pass, winProb = 0.40),
        )
        val q = DecisionQuality.grade(challenge, good)!!
        assertTrue(q.wasChallenge)
        assertTrue(q.challengeGood)

        // A long-shot challenge (low P bluff) is graded NOT good.
        val bad = listOf(
            advice(pass, winProb = 0.55, recommended = true),
            advice(challenge, winProb = 0.30, successOdds = 0.2),
        )
        val q2 = DecisionQuality.grade(challenge, bad)!!
        assertTrue(q2.wasChallenge)
        assertFalse(q2.challengeGood)
    }

    @Test
    fun bluffAction_isFlaggedAsBluff() {
        val list = listOf(
            advice(income, winProb = 0.55, recommended = true),
            advice(tax, winProb = 0.45, bluff = true, truthful = false),
        )
        val q = DecisionQuality.grade(tax, list)!!
        assertTrue(q.wasBluff)
    }

    @Test
    fun forcedSingleOption_isNotGradeable() {
        val list = listOf(advice(income, winProb = 0.5, recommended = true))
        assertNull(DecisionQuality.grade(income, list))
        // Empty advice (coach not landed) is also ungradeable.
        assertNull(DecisionQuality.grade(income, emptyList()))
    }

    @Test
    fun tally_accumulatesAndReconcilesBluffOutcome() {
        val list = listOf(
            advice(tax, winProb = 0.62, recommended = true, truthful = true),
            advice(income, winProb = 0.50),
        )
        val bluffList = listOf(
            advice(income, winProb = 0.55, recommended = true),
            advice(tax, winProb = 0.45, bluff = true, truthful = false),
        )
        val challengeList = listOf(
            advice(challenge, winProb = 0.55, recommended = true, successOdds = 0.7),
            advice(pass, winProb = 0.40),
        )

        var tally = MatchDecisionTally()
        tally += DecisionQuality.grade(tax, list)!!          // matched best, 0 EV
        tally += DecisionQuality.grade(income, list)!!       // missed, 120 milli EV
        tally += DecisionQuality.grade(tax, bluffList)!!     // a bluff attempt
        tally += DecisionQuality.grade(challenge, challengeList)!! // a good challenge

        assertEquals(4, tally.decisions)
        assertEquals(2, tally.matchedBest)                   // tax-best + challenge-best
        // income-miss 0.12 (120) + bluff-tax-miss 0.10 (100) = 220 milli.
        assertEquals(220L, tally.evLostMilli)
        assertEquals(1, tally.challenges)
        assertEquals(1, tally.challengesGood)
        assertEquals(1, tally.bluffsTried)
        assertEquals(50, tally.accuracyPct)                  // 2 / 4

        // At game end: 1 bluff tried, suppose it was NOT caught → 1 survived.
        val reconciled = tally.withBluffOutcome(survivedBluffs = 1)
        assertEquals(1, reconciled.bluffsOk)
        // A noisy scrape can't push success over the attempts count.
        val clamped = tally.withBluffOutcome(survivedBluffs = 9)
        assertEquals(1, clamped.bluffsOk)
    }
}
