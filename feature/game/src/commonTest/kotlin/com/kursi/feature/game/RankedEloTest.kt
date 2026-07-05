package com.kursi.feature.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M6d — ELO ladder invariants. The single most important property: the update is STRICTLY monotonic
 * in the result — a win never lowers the rating, a loss never raises it, regardless of the table's
 * implied opponent rating or rounding.
 */
class RankedEloTest {
    @Test
    fun win_always_increases_rating() {
        // Sweep a wide rating range against every difficulty; a win must strictly raise the rating.
        for (rating in 0..3000 step 50) {
            for (diff in Difficulty.entries) {
                val next = Elo.stepForGame(rating, diff, won = true)
                assertTrue(
                    next > rating,
                    "win at rating=$rating vs $diff produced $next (expected > $rating)",
                )
            }
        }
    }

    @Test
    fun loss_always_decreases_rating() {
        // Above the floor a loss is strictly monotonic; the floor-clamp edge is covered separately.
        for (rating in 50..3000 step 50) {
            for (diff in Difficulty.entries) {
                val next = Elo.stepForGame(rating, diff, won = false)
                assertTrue(
                    next < rating,
                    "loss at rating=$rating vs $diff produced $next (expected < $rating)",
                )
            }
        }
    }

    @Test
    fun harder_table_rewards_a_win_more_and_punishes_a_loss_less() {
        val rating = 1000
        // Beating a Grandmaster table should gain at least as much as beating an Easy table.
        val gainEasy = Elo.stepForGame(rating, Difficulty.Easy, won = true) - rating
        val gainGm = Elo.stepForGame(rating, Difficulty.Grandmaster, won = true) - rating
        assertTrue(gainGm >= gainEasy, "GM win gain $gainGm should be >= Easy win gain $gainEasy")
        // Losing to a Grandmaster table should cost no more than losing to an Easy table.
        val lossEasy = rating - Elo.stepForGame(rating, Difficulty.Easy, won = false)
        val lossGm = rating - Elo.stepForGame(rating, Difficulty.Grandmaster, won = false)
        assertTrue(lossGm <= lossEasy, "GM loss cost $lossGm should be <= Easy loss cost $lossEasy")
    }

    @Test
    fun opponent_rating_orders_by_difficulty() {
        val ratings = Difficulty.entries.map { Elo.opponentRating(it) }
        assertEquals(ratings, ratings.sorted(), "implied opponent ratings must increase with difficulty")
    }

    @Test
    fun rating_is_clamped_to_floor_on_repeated_losses() {
        var r = 50
        repeat(20) { r = Elo.stepForGame(r, Difficulty.Easy, won = false) }
        assertTrue(r >= 0, "rating must not go below 0, was $r")
    }

    @Test
    fun expected_score_is_half_at_equal_rating() {
        val e = Elo.expectedScore(1200, 1200)
        assertTrue(e in 0.49..0.51, "expected score at equal rating should be ~0.5, was $e")
    }
}
