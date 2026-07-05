package com.kursi.feature.game.session

import com.kursi.engine.Intent
import com.kursi.feature.game.Difficulty
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * CompletedMatch — the persisted record of a FINISHED local game (M6c §1).
 *
 * Like [MatchSnapshot] this is deterministic-by-construction: the engine + bots are a pure function
 * of ([seed] + [humanLog]) + the config ([players]/[difficulty]/[humanCount]), so a finished game is
 * fully reviewable from these cheap inputs alone — [ReplaySession] reconstructs every GameState by
 * replaying the log onto a fresh `initialState`. We persist NO `GameState`.
 *
 * M3 cleared the in-progress [MatchSnapshot] on game-over; M6c ADDS this separate completed-match
 * register (capped, most-recent-first) so a finished game can be reviewed afterward. On top of the
 * snapshot inputs it carries the OUTCOME ([winnerSeat]), the [personas] lineup (so the Review UI can
 * name seats without re-deriving them), and the [tally] of the human's decision quality this game.
 *
 * Reuses [SnapIntent]/[SnapAction]/[SnapRole] from [MatchSnapshot] for the value-class codec.
 */
@Serializable
data class CompletedMatch(
    val version: Int = CURRENT_VERSION,
    val seed: Long,
    val players: Int,
    val difficulty: String, // Difficulty.name
    val humanLog: List<SnapIntent>,
    /** Number of hot-seat humans (1 = vs-AI). Lowest [humanCount] seats are human. */
    val humanCount: Int = 1,
    /** The winning seat (raw PlayerId int). */
    val winnerSeat: Int,
    /** The persona lineup that played this game, by seat (human + bot seats both labelled). */
    val personas: List<SnapPersona> = emptyList(),
    /** The human's decision-quality tally for this game. */
    val tally: SnapTally = SnapTally(),
    /**
     * Monotonic ordinal stamped at record time so the Review UI can show "most recent" stably even
     * when two finishes share a wall clock. The store keeps newest-first; this is a secondary tie-break
     * / display hint, NOT a wall-clock timestamp (the engine has no clock).
     */
    val recordedOrdinal: Long = 0L,
) {
    val difficultyEnum: Difficulty
        get() = Difficulty.entries.firstOrNull { it.name == difficulty } ?: Difficulty.Medium

    /** True when the human (seat 0, the canonical "you" seat) won this match. */
    val humanWon: Boolean get() = winnerSeat == 0

    fun encode(): String = json.encodeToString(this)

    /** The seed + human log distilled back into a [MatchSnapshot] for [ReplaySession] / [GameSession.restore]. */
    fun toSnapshot(): MatchSnapshot =
        MatchSnapshot(
            seed = seed,
            players = players,
            difficulty = difficulty,
            humanLog = humanLog,
            humanCount = humanCount,
        )

    companion object {
        const val CURRENT_VERSION = 1
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        /** Decode a persisted record; returns null on any corruption or version mismatch. */
        fun decode(raw: String?): CompletedMatch? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString<CompletedMatch>(raw) }
                .getOrNull()
                ?.takeIf { it.version == CURRENT_VERSION }
        }

        /** Decode a whole list of persisted records (most-recent first), dropping any corrupt entry. */
        fun decodeAll(raws: List<String>): List<CompletedMatch> = raws.mapNotNull { decode(it) }

        /** Build a finished-match record from a live session's identity + outcome. */
        fun of(
            seed: Long,
            players: Int,
            difficulty: Difficulty,
            humanLog: List<Intent>,
            winnerSeat: Int,
            humanCount: Int = 1,
            personas: List<SnapPersona> = emptyList(),
            tally: SnapTally = SnapTally(),
            recordedOrdinal: Long = 0L,
        ): CompletedMatch =
            CompletedMatch(
                seed = seed,
                players = players,
                difficulty = difficulty.name,
                humanLog = humanLog.map { it.toSnap() },
                humanCount = humanCount,
                winnerSeat = winnerSeat,
                personas = personas,
                tally = tally,
                recordedOrdinal = recordedOrdinal,
            )
    }
}

/**
 * Serializable persona lineup entry — one seat's display identity at game time. Mirrors
 * `com.kursi.feature.game.OpponentPersona` (kept here so :core has no dep on the persona type and the
 * record self-describes the table for the Review UI).
 */
@Serializable
data class SnapPersona(
    val seat: Int,
    val name: String,
    val monogram: String,
    val seatColorArgb: Long,
    /** True for a human (hot-seat) seat; false for a bot persona. */
    val isHuman: Boolean = false,
)

/**
 * Serializable mirror of `com.kursi.feature.game.MatchDecisionTally` — the human's per-game
 * decision-quality counters, persisted with the finished match so the Review UI can show how the
 * game was played without recomputing the coach.
 */
@Serializable
data class SnapTally(
    val decisions: Int = 0,
    val matchedBest: Int = 0,
    val evLostMilli: Long = 0L,
    val challenges: Int = 0,
    val challengesGood: Int = 0,
    val bluffsTried: Int = 0,
    val bluffsOk: Int = 0,
) {
    /** % of gradeable decisions matching the advisor best this game; 0 when none. */
    val accuracyPct: Int get() = if (decisions == 0) 0 else (matchedBest * 100 / decisions)
}
