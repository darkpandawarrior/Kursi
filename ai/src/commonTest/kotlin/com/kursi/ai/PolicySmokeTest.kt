package com.kursi.ai

import com.kursi.engine.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Smoke tests — full games complete without crashing and produce valid results.
 * Verifies that EasyPolicy and MediumPolicy never return an illegal intent.
 */
class PolicySmokeTest {
    private fun makePolicies(
        n: Int,
        factory: (Int, Long) -> Policy,
    ): Map<PlayerId, Policy> = (0 until n).associate { seat -> PlayerId(seat) to factory(seat, seat.toLong() * 1000L + 42L) }

    @Test
    fun easyPolicy_completesGames_2to6Players() {
        for (n in 2..6) {
            val config = GameConfig.forPlayers(n)
            repeat(5) { gameIdx ->
                val policies = makePolicies(n) { seat, _ -> EasyPolicy(seed = (gameIdx * 100L + seat)) }
                val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 7L + n, policies)
                assertTrue(result.winner.raw in 0 until n, "n=$n game=$gameIdx: winner ${result.winner} out of range")
                assertTrue(result.turns > 0, "n=$n game=$gameIdx: turns must be > 0")
            }
        }
    }

    @Test
    fun mediumPolicy_completesGames_2to6Players() {
        for (n in 2..6) {
            val config = GameConfig.forPlayers(n)
            repeat(5) { gameIdx ->
                val policies = makePolicies(n) { seat, _ -> MediumPolicy(seed = (gameIdx * 100L + seat)) }
                val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 13L + n, policies)
                assertTrue(result.winner.raw in 0 until n, "n=$n game=$gameIdx: winner ${result.winner} out of range")
                assertTrue(result.turns > 0, "n=$n game=$gameIdx: turns must be > 0")
            }
        }
    }

    @Test
    fun mixedPolicy_easyAndMedium_completesGames() {
        val config = GameConfig.forPlayers(4)
        repeat(5) { gameIdx ->
            val policies: Map<PlayerId, Policy> =
                mapOf(
                    PlayerId(0) to EasyPolicy(seed = gameIdx * 10L),
                    PlayerId(1) to MediumPolicy(seed = gameIdx * 10L + 1),
                    PlayerId(2) to EasyPolicy(seed = gameIdx * 10L + 2),
                    PlayerId(3) to MediumPolicy(seed = gameIdx * 10L + 3),
                )
            val result = SimHarness.playOut(config, seed = gameIdx.toLong() + 100L, policies)
            assertTrue(result.winner.raw in 0 until 4, "mixed 4p game=$gameIdx: winner out of range")
        }
    }
}
