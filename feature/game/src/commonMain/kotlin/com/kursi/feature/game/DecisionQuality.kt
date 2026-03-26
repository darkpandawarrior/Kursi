package com.kursi.feature.game

import com.kursi.ai.advisor.MoveAdvice
import com.kursi.engine.Intent
import kotlin.math.roundToLong

/**
 * M6b ANALYTICS — decision-quality tracking.
 *
 * The decision-coach already runs [com.kursi.ai.advisor.MoveAdvisor.advise] on every human decision
 * (off the UI thread, FAIR / internally redacted). M6b reuses that exact ranked read to *grade* the
 * move the human actually played, then folds the grades into a per-game [MatchDecisionTally] which the
 * app layer persists into the lifetime decision-quality ledger.
 *
 * SECRECY / FAIRNESS: this never inspects hidden cards. It only consumes the public advisor read and
 * the human's own chosen [Intent] — the same information the coach already surfaces.
 */

/**
 * The advisor's grade of ONE human decision, computed from the ranked [MoveAdvice] list for that
 * decision and the [Intent] the human chose. Only decisions with ≥ 2 legal moves are graded (a forced
 * single-legal move is not a "decision" — grading it would dilute the accuracy stat).
 */
data class DecisionQuality(
    /** True when the human played the advisor's single recommended move. */
    val matchedBest: Boolean,
    /** win-probability lost vs the best move, in [0,1], clamped ≥ 0. */
    val evLost: Double,
    /** True when the chosen intent was a Challenge. */
    val wasChallenge: Boolean,
    /** For a challenge: true when it was +EV per the read (P(opponent bluffing) ≥ 0.5). */
    val challengeGood: Boolean,
    /** True when the chosen intent was a bluff action/block (claimed a role the human did not hold). */
    val wasBluff: Boolean,
) {
    /** EV lost as integer milli-units (×1000) for the persisted ledger. */
    val evLostMilli: Long get() = (evLost.coerceAtLeast(0.0) * 1000.0).roundToLong()

    companion object {
        /**
         * Grade [chosen] against the ranked [advice] for this decision. Returns null when the decision
         * is not gradeable: no advice computed yet, fewer than 2 options, or the chosen intent is not
         * in the advice (a stale/illegal tap — never happens on the accepted path, but defensive).
         */
        fun grade(chosen: Intent, advice: List<MoveAdvice>): DecisionQuality? {
            if (advice.size < 2) return null
            val chosenAdvice = advice.firstOrNull { it.intent == chosen } ?: return null
            val best = advice.firstOrNull { it.recommended } ?: advice.maxByOrNull { it.winProb } ?: return null

            val matched = chosenAdvice.intent == best.intent
            val evLost = (best.winProb - chosenAdvice.winProb).coerceAtLeast(0.0)

            val wasChallenge = chosen is Intent.Challenge
            // successOdds for a Challenge = P(opponent is bluffing); ≥ 0.5 → the challenge is +EV.
            val challengeGood = wasChallenge && (chosenAdvice.successOdds ?: 0.0) >= 0.5

            // truthful == false marks a bluff (claimed a role not held). null/true are not bluffs.
            val wasBluff = chosenAdvice.bluff

            return DecisionQuality(
                matchedBest = matched,
                evLost = evLost,
                wasChallenge = wasChallenge,
                challengeGood = challengeGood,
                wasBluff = wasBluff,
            )
        }
    }
}

/**
 * One game's running decision-quality accumulator (M6b). The ViewModel folds a [DecisionQuality] in
 * after every gradeable human move via [plus], and reconciles bluff outcomes (tried vs survived) at
 * game end via [withBluffOutcome]. The app layer reads the final tally and persists it.
 *
 * Immutable / value-typed so it survives the MVI state contract cleanly and stays test-friendly.
 */
data class MatchDecisionTally(
    val decisions: Int = 0,
    val matchedBest: Int = 0,
    val evLostMilli: Long = 0L,
    val challenges: Int = 0,
    val challengesGood: Int = 0,
    val bluffsTried: Int = 0,
    val bluffsOk: Int = 0,
) {
    /** Fold one graded decision into the running tally. */
    operator fun plus(q: DecisionQuality): MatchDecisionTally = copy(
        decisions = decisions + 1,
        matchedBest = matchedBest + if (q.matchedBest) 1 else 0,
        evLostMilli = evLostMilli + q.evLostMilli,
        challenges = challenges + if (q.wasChallenge) 1 else 0,
        challengesGood = challengesGood + if (q.wasChallenge && q.challengeGood) 1 else 0,
        bluffsTried = bluffsTried + if (q.wasBluff) 1 else 0,
        // bluffsOk is reconciled at game end from the public reveal log — not known at decision time.
    )

    /**
     * Reconcile bluff *outcomes* at game end. [survivedBluffs] is the count of the human's bluffs that
     * were NOT caught (i.e. bluffsTried − bluffsCaught from the M3 reveal scrape), clamped into
     * [0, bluffsTried] so a noisy scrape can never make the success rate exceed 100%.
     */
    fun withBluffOutcome(survivedBluffs: Int): MatchDecisionTally =
        copy(bluffsOk = survivedBluffs.coerceIn(0, bluffsTried))

    /** True when nothing worth persisting accrued (so an empty game doesn't churn the store). */
    val isEmpty: Boolean get() = decisions == 0 && challenges == 0 && bluffsTried == 0

    /** % of this game's gradeable decisions matching the advisor best; 0 when none. */
    val accuracyPct: Int get() = if (decisions == 0) 0 else (matchedBest * 100 / decisions)
}
