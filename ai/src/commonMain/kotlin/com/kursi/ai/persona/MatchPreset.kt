package com.kursi.ai.persona

/**
 * A curated, on-identity "ready-made requisition" — one tap configures an entire match:
 * seat count, bot difficulty, and a curated persona lineup.
 *
 * The lineup is realised through the existing seed-deterministic [PersonaAssigner.assign]: each
 * preset carries a [seed] hand-picked so the roster the player sees in the Lobby (and faces at the
 * table) is the intended cast. Because the whole game flow is already seed-addressed
 * (Setup → Lobby → Game all key off the seed), a preset is just a pre-filled Setup choice — it needs
 * NO new engine/session plumbing, and the deal stays fully deterministic and resumable.
 *
 * @property id          Stable preset id (analytics / persistence key).
 * @property playerCount Total seats at the table (2..10), including the human seat 0.
 * @property difficulty  Bot tier for every opponent seat.
 * @property seed        Fixed deterministic seed → reproducible curated lineup every time.
 */
data class MatchPreset(
    val id: String,
    val playerCount: Int,
    val difficulty: BotDifficulty,
    val seed: Long,
) {
    /**
     * The curated opponent lineup this preset deals, in seat order (seats 1..playerCount-1).
     * Derived from the same deterministic assignment the live game uses, so the Lobby preview and
     * the in-game table always match. The human (seat 0) is not included.
     */
    fun lineup(): List<BotPersona> =
        PersonaAssigner.assign(
            seatCount = playerCount - 1,
            difficulty = difficulty,
            seed = seed,
        ).map { it.first }

    companion object {
        /** "Classic Cabinet" — a balanced 4-player mixed cabinet on the Permanent Babu (Medium) tier. */
        val CLASSIC_CABINET = MatchPreset(
            id = "classic_cabinet",
            playerCount = 4,
            difficulty = BotDifficulty.MEDIUM,
            seed = 1947L, // Independence-year wink; deals a varied, non-degenerate 3-bot cabinet.
        )

        /** "The Snake Pit" — sharp, aggressive opponents on the Section Officer (Hard) tier. */
        val SNAKE_PIT = MatchPreset(
            id = "snake_pit",
            playerCount = 4,
            difficulty = BotDifficulty.HARD,
            seed = 1975L, // Emergency-year wink; a knife-edge table that smells a lie.
        )

        /** "Chaos · 10" — the full ten-seat mehfil; every persona in the room on Medium. */
        val CHAOS_TEN = MatchPreset(
            id = "chaos_ten",
            playerCount = 10,
            difficulty = BotDifficulty.MEDIUM,
            seed = 1991L, // Liberalisation-year wink; the whole bazaar at one table.
        )

        /** All named presets, in display order. */
        val ALL: List<MatchPreset> = listOf(CLASSIC_CABINET, SNAKE_PIT, CHAOS_TEN)
    }
}
