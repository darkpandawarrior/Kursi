package com.kursi.feature.game

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * M6d — pure ELO ladder math for the local ranked ladder. No clock, no I/O, no Compose: just the
 * rating step and the table-difficulty → implied-opponent-rating mapping. Deterministic and testable.
 *
 * The player carries a single persisted rating (in [com.kursi.core.prefs.AppPrefs]); each finished
 * game is scored as a 1-v-"table" bout where the table's difficulty implies an opponent rating. A win
 * always nudges the rating up, a loss always nudges it down (strictly monotonic per result) — the
 * magnitude scales with how surprising the result was (the standard ELO expected-score curve).
 */
object Elo {

    /** K-factor — the maximum single-game swing magnitude. A moderate value for a casual ladder. */
    const val K = 32.0

    /**
     * Implied opponent rating for a table of the given [Difficulty]. Easy < Medium < Hard < Expert <
     * Grandmaster, spanning the rank ladder so beating a Grandmaster table climbs you fast while
     * beating an Easy table barely moves you (and losing to it stings).
     */
    fun opponentRating(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.Easy        -> 850
        Difficulty.Medium      -> 1050
        Difficulty.Hard        -> 1300
        Difficulty.Expert      -> 1600
        Difficulty.Grandmaster -> 1900
    }

    /** Expected score for [rating] against [opponent] — the logistic ELO curve, in (0,1). */
    fun expectedScore(rating: Int, opponent: Int): Double =
        1.0 / (1.0 + 10.0.pow((opponent - rating) / 400.0))

    /**
     * One ELO step. Returns the NEW rating after a game vs an opponent of [opponentRating], where
     * [won] is the actual result. The change is `K * (actual - expected)`; a win rounds up by at
     * least +1 and a loss rounds down by at least -1, so the update is STRICTLY monotonic in the
     * result regardless of rounding (a key invariant the ladder tests lock down).
     */
    fun step(rating: Int, opponentRating: Int, won: Boolean): Int {
        val expected = expectedScore(rating, opponentRating)
        val actual = if (won) 1.0 else 0.0
        val rawDelta = K * (actual - expected)
        val delta = if (won) {
            // A win never decreases the rating: at least +1, even vs a far-weaker table.
            rawDelta.roundToInt().coerceAtLeast(1)
        } else {
            // A loss never increases the rating: at most -1, even vs a far-stronger table.
            rawDelta.roundToInt().coerceAtMost(-1)
        }
        return (rating + delta).coerceIn(0, 4000)
    }

    /** Convenience: the new rating after a finished game on a table of [difficulty]. */
    fun stepForGame(rating: Int, difficulty: Difficulty, won: Boolean): Int =
        step(rating, opponentRating(difficulty), won)
}
