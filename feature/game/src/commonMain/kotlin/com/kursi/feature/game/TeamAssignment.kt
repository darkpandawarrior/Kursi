package com.kursi.feature.game

/**
 * M6e — TEAM KHEL assignment. Maps every seat (0 until [playerCount]) to a team id for the engine's
 * TEAMS variant ([com.kursi.engine.GameConfig.teams]). The win condition becomes "last team standing".
 *
 * The partition is the simple, legible "alternating seats" scheme: seat `i` joins team `i % teamCount`.
 * For the canonical 2-team game this is the classic A/B/A/B/… split (seat 0 = the human anchors team A).
 * Deterministic and total over seats — exactly what [com.kursi.engine.GameConfig]'s teams contract needs
 * (every seat mapped, ≥ 2 distinct teams).
 *
 * Free-for-all is represented by a null map (no teams) — never call this for a 1-team request.
 */
object TeamAssignment {
    /** Default and only currently-surfaced team count: two factions (Sat Paksh vs Vipaksh). */
    const val DEFAULT_TEAM_COUNT = 2

    /**
     * Build the seat→team map for [playerCount] seats split into [teamCount] teams (alternating). Returns
     * null when [teamCount] < 2 (free-for-all) or when the split would leave a team empty (e.g. more teams
     * than seats), so the caller falls back to classic play rather than building an invalid config.
     */
    fun build(
        playerCount: Int,
        teamCount: Int = DEFAULT_TEAM_COUNT,
    ): Map<Int, Int>? {
        if (teamCount < 2) return null
        if (playerCount < teamCount) return null // can't fill every team
        val map = (0 until playerCount).associateWith { it % teamCount }
        // Safety: the engine requires >= 2 distinct teams; alternating over >= teamCount seats guarantees it.
        return if (map.values.toSet().size >= 2) map else null
    }

    /** Team id of [seat] under an alternating [teamCount]-way split. */
    fun teamOfSeat(
        seat: Int,
        teamCount: Int = DEFAULT_TEAM_COUNT,
    ): Int = seat % teamCount
}
