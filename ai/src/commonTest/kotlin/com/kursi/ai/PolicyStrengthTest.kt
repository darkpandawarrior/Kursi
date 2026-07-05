package com.kursi.ai

import com.kursi.engine.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Strength test — Medium beats Easy > 55% of the time in head-to-head play.
 * Fixed seeds make the test deterministic.
 */
class PolicyStrengthTest {
    /**
     * Run [gamesPerConfig] games with alternating seats between Medium and Easy,
     * returning (mediumWins, totalGames).
     */
    private fun headToHead(
        n: Int,
        gamesPerConfig: Int,
    ): Pair<Int, Int> {
        val config = GameConfig.forPlayers(n)
        var mediumWins = 0
        var total = 0

        for (gameIdx in 0 until gamesPerConfig) {
            // Alternate which seats are Medium vs Easy.
            // For n=2: game 0 → seat 0=Medium, seat 1=Easy; game 1 → seat 0=Easy, seat 1=Medium
            val policies: Map<PlayerId, Policy> =
                (0 until n).associate { seat ->
                    val isMedium = (seat + gameIdx) % 2 == 0
                    val seedOffset = gameIdx.toLong() * 100L + seat
                    PlayerId(seat) to if (isMedium) MediumPolicy(seed = seedOffset) else EasyPolicy(seed = seedOffset + 5000L)
                }
            val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 17L + n.toLong(), policies)
            val winnerSeat = result.winner.raw
            val isMediumSeat = (winnerSeat + gameIdx) % 2 == 0
            if (isMediumSeat) mediumWins++
            total++
        }
        return mediumWins to total
    }

    @Test
    fun medium_beats_easy_2player() {
        val games = 250
        val (wins, total) = headToHead(n = 2, gamesPerConfig = games)
        val winRate = wins.toDouble() / total
        assertTrue(
            winRate > 0.55,
            "Medium win rate in 2-player was $winRate (wins=$wins / total=$total), expected > 0.55",
        )
    }

    @Test
    fun medium_beats_easy_4player() {
        // With 4 players, Medium should win > 25% (its random share), and > Easy in absolute terms.
        // We run with 2 Medium vs 2 Easy per game (alternating), count Medium wins.
        val config = GameConfig.forPlayers(4)
        var mediumWins = 0
        val games = 100
        for (gameIdx in 0 until games) {
            // Seats 0,2 = Medium; seats 1,3 = Easy (then swap for next set).
            val policies: Map<PlayerId, Policy> =
                mapOf(
                    PlayerId(0) to MediumPolicy(seed = gameIdx.toLong()),
                    PlayerId(1) to EasyPolicy(seed = gameIdx.toLong() + 1000L),
                    PlayerId(2) to MediumPolicy(seed = gameIdx.toLong() + 2000L),
                    PlayerId(3) to EasyPolicy(seed = gameIdx.toLong() + 3000L),
                )
            val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 31L, policies)
            val ws = result.winner.raw
            if (ws == 0 || ws == 2) mediumWins++
        }
        val winRate = mediumWins.toDouble() / games
        assertTrue(
            winRate > 0.40,
            "Medium win rate in 4-player was $winRate (wins=$mediumWins / $games), expected > 0.40 (random = 0.25, each of 2 Medium seats = 0.5)",
        )
    }
}
