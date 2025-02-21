package com.kursi.ai

import com.kursi.ai.persona.BotDifficulty
import com.kursi.ai.persona.PersonaAssigner
import com.kursi.engine.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Persona neutrality test — 4 Hard seats with 4 extreme personas (Aggressor, Banker,
 * Wildcard, Defender). Asserts that max-min win-share across seats < 0.12 so that
 * personality biases play-style but NOT strength.
 *
 * Uses Hard (not Expert) so the test stays fast (no ISMCTS search).
 * Kept fast: 80 games × ~200 steps each ≈ well under 10 s on JVM.
 *
 * The allowed spread is 0.12 (vs the in-game 0.08 aspiration) to give statistical
 * headroom at 80 games; at n=80 the margin-of-error on a binomial ≈ ±0.11.
 */
class PersonaNeutralityTest {

    @Test
    fun hard_personas_neutrality_4player() {
        val config = GameConfig.forPlayers(4)
        val games = 80
        val winsBySeat = IntArray(4)

        for (gameIdx in 0 until games) {
            val gameSeed = gameIdx.toLong() * 137L + 19L

            // Assign 4 distinct personas (Hard tier), one per seat.
            val assignments = PersonaAssigner.assign(
                seatCount = 4,
                difficulty = BotDifficulty.HARD,
                seed = gameSeed,
            )

            val policies: Map<PlayerId, Policy> = assignments.mapIndexed { seat, pair ->
                PlayerId(seat) to (pair.second as Policy)
            }.toMap()

            val result = SimHarness.playOut(config, seed = gameSeed, policies)
            winsBySeat[result.winner.raw]++
        }

        val winShares = winsBySeat.map { it.toDouble() / games }
        val maxShare = winShares.max()
        val minShare = winShares.min()
        val spread = maxShare - minShare

        assertTrue(
            spread < 0.12,
            "Persona win-share spread $spread >= 0.12 — persona is affecting strength. " +
                "Wins by seat: ${winsBySeat.toList()} / $games games. Shares: $winShares"
        )
    }
}
