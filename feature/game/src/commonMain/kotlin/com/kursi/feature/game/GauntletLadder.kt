package com.kursi.feature.game

/**
 * M6e — the GAUNTLET ladder (Tarakki ki Seedhi): an escalating single-player bracket. The player must
 * beat a table at each difficulty rung in sequence — Easy, then Medium, Hard, Expert, Grandmaster — to
 * be PROMOTED to the next. On-identity: a sarkari "promotion ladder", from Probationer to the corner
 * office. Progress is persisted via [com.kursi.core.prefs.GauntletProgress] (rung index + win count).
 *
 * Each [GauntletRung] is a fully-resolved match config (difficulty + seat count + a fixed deterministic
 * [seed]) so a rung deals the SAME curated table every attempt — same lineup, same deal — exactly like
 * [com.kursi.ai.persona.MatchPreset]. Because the whole flow is seed-addressed (Setup → Lobby → Game),
 * a rung needs NO new engine/session plumbing; it is just a pre-filled match choice that records its
 * result back into the ladder on game-over.
 */
data class GauntletRung(
    /** 0-based rung index into [GauntletLadder.RUNGS]. */
    val index: Int,
    /** Stable id (analytics / persistence). */
    val id: String,
    /** The opponent difficulty for this rung. */
    val difficulty: Difficulty,
    /** Total seats at the table (human seat 0 + bots). */
    val players: Int,
    /** Fixed deterministic seed → the same curated table on every attempt of this rung. */
    val seed: Long,
)

/**
 * The fixed five-rung ladder. Seat counts widen mildly as the rungs climb so the gauntlet escalates on
 * BOTH axes (smarter bots AND a fuller table). Seeds are hand-picked era winks (like the presets) so
 * each rung deals a varied, non-degenerate cast.
 */
object GauntletLadder {
    val RUNGS: List<GauntletRung> =
        listOf(
            GauntletRung(index = 0, id = "probationer", difficulty = Difficulty.Easy, players = 3, seed = 1947L),
            GauntletRung(index = 1, id = "clerk", difficulty = Difficulty.Medium, players = 4, seed = 1962L),
            GauntletRung(index = 2, id = "officer", difficulty = Difficulty.Hard, players = 4, seed = 1975L),
            GauntletRung(index = 3, id = "secretary", difficulty = Difficulty.Expert, players = 5, seed = 1991L),
            GauntletRung(index = 4, id = "cabinet", difficulty = Difficulty.Grandmaster, players = 6, seed = 2014L),
        )

    /** The last valid rung index — i.e. the top of the ladder. */
    val lastIndex: Int get() = RUNGS.lastIndex

    /** The rung at [index], clamped into range (so an over-cleared ladder still resolves to the top). */
    fun rungAt(index: Int): GauntletRung = RUNGS[index.coerceIn(0, lastIndex)]
}
