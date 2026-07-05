package com.kursi.feature.game.session

import com.kursi.ai.persona.BotDifficulty
import com.kursi.ai.persona.PersonaAssigner
import com.kursi.ai.persona.PersonaPolicy
import com.kursi.engine.GameConfig
import com.kursi.engine.PlayerId
import com.kursi.engine.Policy
import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.OpponentPersona

/**
 * MatchReplay — the Review-phase entry point: turn a persisted [CompletedMatch] into a deterministic
 * [ReplaySession] (M6c).
 *
 * The bots are reconstructed from the SEED via [PersonaAssigner] EXACTLY as `GameViewModel.startGame`
 * does, so the replay uses the identical policies the live match used — that, plus the recorded human
 * log, makes the reconstructed GameState sequence bit-for-bit identical to the original (M6c §3). The
 * persona LINEUP for display is taken from the recorded [CompletedMatch.personas] (no need to re-derive
 * names), but the policy seats are rebuilt deterministically.
 */
object MatchReplay {
    /** Map the feature [Difficulty] to the AI [BotDifficulty] — identical to the live game mapping. */
    private fun botDifficultyOf(difficulty: Difficulty): BotDifficulty =
        when (difficulty) {
            Difficulty.Easy -> BotDifficulty.EASY
            Difficulty.Medium -> BotDifficulty.MEDIUM
            Difficulty.Hard -> BotDifficulty.HARD
            Difficulty.Expert -> BotDifficulty.EXPERT
            Difficulty.Grandmaster -> BotDifficulty.GRANDMASTER
        }

    /**
     * Build a [ReplaySession] from a recorded [match]. Reconstructs the bot policies deterministically
     * from the seed (the same assignment `startGame` performs) and feeds the recorded human log to
     * [ReplaySession.build]. The resulting session steps through the human-decision frames, redacted.
     */
    fun replaySessionFor(match: CompletedMatch): ReplaySession {
        val config = GameConfig.forPlayers(match.players)
        val humanCount = match.humanCount.coerceIn(1, config.seatCount)
        val humanSeats = (0 until humanCount).map { PlayerId(it) }.toSet()
        val botSeatCount = config.seatCount - humanCount

        // Rebuild the bot policies from the seed — deterministic, so identical to the live match.
        val assignments =
            if (botSeatCount > 0) {
                PersonaAssigner.assign(
                    seatCount = botSeatCount,
                    difficulty = botDifficultyOf(match.difficultyEnum),
                    seed = match.seed,
                )
            } else {
                emptyList()
            }

        val bots = mutableMapOf<PlayerId, Policy>()
        assignments.forEachIndexed { index, (_, policy: PersonaPolicy) ->
            bots[PlayerId(humanCount + index)] = policy
        }

        // Display lineup from the RECORD (names/colours frozen at game time).
        val personas: Map<PlayerId, OpponentPersona> =
            match.personas.associate { p ->
                PlayerId(p.seat) to
                    OpponentPersona(
                        playerId = PlayerId(p.seat),
                        name = p.name,
                        monogram = p.monogram,
                        seatColorArgb = p.seatColorArgb,
                    )
            }

        return ReplaySession.build(
            snapshot = match.toSnapshot(),
            humanSeats = humanSeats,
            bots = bots,
            winnerSeat = match.winnerSeat,
            personas = personas,
        )
    }
}
