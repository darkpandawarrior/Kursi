package com.kursi.ai

import com.kursi.engine.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for HardPolicy:
 *   - smoke: full games (N=2..6) complete without exception.
 *   - strength: Hard beats Medium > 55% of the time in N=2 head-to-head.
 *               Hard combined win-rate > 50% in N=4 mixed (2 Hard vs 2 Medium).
 */
class HardPolicyTest {
    // ── Smoke ─────────────────────────────────────────────────────────────────

    @Test
    fun hardPolicy_completesGames_2to6Players() {
        for (n in 2..6) {
            val config = GameConfig.forPlayers(n)
            repeat(5) { gameIdx ->
                val policies: Map<PlayerId, Policy> =
                    (0 until n).associate { seat ->
                        PlayerId(seat) to HardPolicy(seed = gameIdx.toLong() * 100L + seat)
                    }
                val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 11L + n, policies)
                assertTrue(result.winner.raw in 0 until n, "n=$n game=$gameIdx: winner ${result.winner} out of range")
                assertTrue(result.turns > 0, "n=$n game=$gameIdx: turns must be > 0")
            }
        }
    }

    @Test
    fun hardPolicy_mixedWithMedium_completesGames() {
        val config = GameConfig.forPlayers(4)
        repeat(5) { gameIdx ->
            val policies: Map<PlayerId, Policy> =
                mapOf(
                    PlayerId(0) to HardPolicy(seed = gameIdx * 10L),
                    PlayerId(1) to MediumPolicy(seed = gameIdx * 10L + 1),
                    PlayerId(2) to HardPolicy(seed = gameIdx * 10L + 2),
                    PlayerId(3) to MediumPolicy(seed = gameIdx * 10L + 3),
                )
            val result = SimHarness.playOut(config, seed = gameIdx.toLong() + 200L, policies)
            assertTrue(result.winner.raw in 0 until 4, "hard+medium 4p game=$gameIdx: winner out of range")
        }
    }

    // ── Strength: Hard vs Medium head-to-head ─────────────────────────────────

    @Test
    fun hard_beats_medium_2player() {
        val games = 200
        val config = GameConfig.forPlayers(2)
        var hardWins = 0

        for (gameIdx in 0 until games) {
            // Alternate seats so neither Hard nor Medium gets structural advantage.
            // Even gameIdx: Hard = seat 0, Medium = seat 1.
            // Odd gameIdx:  Hard = seat 1, Medium = seat 0.
            val hardSeat = gameIdx % 2
            val medSeat = 1 - hardSeat
            val policies: Map<PlayerId, Policy> =
                mapOf(
                    PlayerId(hardSeat) to HardPolicy(seed = gameIdx.toLong() * 100L + hardSeat),
                    PlayerId(medSeat) to MediumPolicy(seed = gameIdx.toLong() * 100L + medSeat + 5000L),
                )
            val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 17L + 2L, policies)
            if (result.winner.raw == hardSeat) hardWins++
        }

        val winRate = hardWins.toDouble() / games
        assertTrue(
            winRate > 0.55,
            "Hard win rate in 2-player was $winRate (wins=$hardWins / total=$games), expected > 0.55",
        )
    }

    @Test
    fun hard_beats_medium_4player_mixed() {
        // 2 Hard (seats 0, 2) vs 2 Medium (seats 1, 3).
        // Hard combined win-rate > 0.50 (random share = 0.50, so > baseline = skill is expressed).
        // 200 games to reduce variance (at ~53% win rate, σ ≈ 0.035, so 3σ below mean ≈ 0.43 > threshold).
        val config = GameConfig.forPlayers(4)
        var hardWins = 0
        val games = 200

        for (gameIdx in 0 until games) {
            val policies: Map<PlayerId, Policy> =
                mapOf(
                    PlayerId(0) to HardPolicy(seed = gameIdx.toLong()),
                    PlayerId(1) to MediumPolicy(seed = gameIdx.toLong() + 1000L),
                    PlayerId(2) to HardPolicy(seed = gameIdx.toLong() + 2000L),
                    PlayerId(3) to MediumPolicy(seed = gameIdx.toLong() + 3000L),
                )
            val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 31L, policies)
            val ws = result.winner.raw
            if (ws == 0 || ws == 2) hardWins++
        }

        val winRate = hardWins.toDouble() / games
        assertTrue(
            winRate > 0.50,
            "Hard combined win rate in 4-player was $winRate (wins=$hardWins / $games), expected > 0.50",
        )
    }
}
