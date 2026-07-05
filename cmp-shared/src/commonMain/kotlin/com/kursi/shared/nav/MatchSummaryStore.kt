package com.kursi.shared.nav

import com.kursi.feature.game.Difficulty

/**
 * MatchSummary — a serializable snapshot of game results.
 * Read by ResultsScreen; does NOT hold a live GameViewModel reference.
 *
 * Computed from GameUiState.toMatchSummary() the moment the game ends.
 */
data class MatchSummary(
    val matchId: String,
    val seed: Long,
    val players: Int,
    val difficulty: Difficulty,
    val winnerSeat: Int?,
    val winnerName: String?,
    val winnerMonogram: String?,
    val winnerColor: Long,
    val humanWon: Boolean,
    val turnsTotal: Int,
    val bluffsHeld: Int,
    val bluffsCaught: Int,
    val recentEvents: List<String>,
    val finalStandings: List<String>,
    val bestMomentPersonaId: String?,
    /**
     * M6b — this game's decision-quality summary (best-move match %, avg EV bled, challenge accuracy).
     * Null when no gradeable decisions were recorded (e.g. a one-move blowout), in which case the
     * Results recap simply omits the line. Populated from the live VM's [com.kursi.feature.game.MatchDecisionTally].
     */
    val decisionSummary: MatchDecisionSummary? = null,
    /** Gauntlet rung index this match was played on, or -1 for a non-gauntlet game. */
    val gauntletRung: Int = -1,
)

/**
 * M6b — a serializable per-game decision-quality snapshot for the Results recap line. Mirrors the
 * counters the ViewModel accumulates; the percentages are precomputed so the (nav-model) screen needs
 * no math.
 */
data class MatchDecisionSummary(
    val decisions: Int,
    val accuracyPct: Int,
    val avgEvLostPct: Int,
    val challenges: Int,
    val challengeAccuracyPct: Int,
)

/**
 * In-memory store for match summaries.
 * Key = matchId (a simple increment); clears on process death (acceptable for M0).
 * Results navigates here via matchId so the engine VM can be torn down immediately.
 */
object MatchSummaryStore {
    private var counter = 0
    private val store = mutableMapOf<String, MatchSummary>()

    /** Store [summary] and return the id key. */
    fun put(summary: MatchSummary): String {
        val id = "match_${++counter}"
        store[id] = summary
        return id
    }

    /**
     * Retrieve a summary by id, or null on a cache miss (process death cleared the in-memory store).
     * ResultsScreen renders an honest "record expired" empty state for null rather than a fabricated
     * 0-turn certificate that misrepresents a game that was actually played (M3 §4).
     */
    fun get(id: String): MatchSummary? = store[id]
}
