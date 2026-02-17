package com.kursi.ai.persona

/**
 * A bot seat's full identity: visual/flavor fields + the behavioral PersonalityProfile.
 *
 * [seatColorArgb] is a locked Okabe-Ito role hue or the reserved slate #56638A —
 * never an invented color. Stored as a Long ARGB so it is platform-agnostic
 * (the UI layer converts to Color as needed).
 *
 * [monogram] is a 2-letter crest label.  [barks] are event-keyed flavor lines.
 */
data class BotPersona(
    val id: String,
    val name: String,
    val title: String,
    val archetype: String,
    /** ARGB packed Long — e.g. 0xFF0072B2L for Neta blue. */
    val seatColorArgb: Long,
    val monogram: String,
    val personality: PersonalityProfile,
    val barks: BarkSet,
)

/** Events that trigger a bark line. */
enum class BarkEvent {
    ACT, BLUFF_DECLARE, BLOCK, CHALLENGED, WIN_CHALLENGE, LOSE_CHALLENGE, WIN_GAME, ELIMINATED, TAUNT
}

/**
 * A set of bark lines keyed by event.
 * Each event maps to a list of candidates; callers pick one (typically at random).
 */
data class BarkSet(
    val lines: Map<BarkEvent, List<String>>,
) {
    fun linesFor(event: BarkEvent): List<String> = lines[event] ?: emptyList()
}
