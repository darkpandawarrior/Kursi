package com.kursi.feature.game.narrative

/**
 * The four player-initiable narrative arcs — the "kissa" (story) threads a player can pull the
 * table into from chat. Each is a small branching script with REAL social-state consequences
 * (alliances, threat redistribution, flaw agitation) — see [com.kursi.feature.game.narrative.StoryArc].
 *
 * - [GATHBANDHAN] Coalition: forge a secret pact, coordinate against a shared rival, then betray.
 * - [AFWAAH]      Rumour: weaponise a target's PARANOIA so the table gangs up on a scapegoat.
 * - [STING]       Honeytrap: flatter a GREEDY/EGO bot into a doomed overreach.
 * - [BADLA]       Vendetta: redirect a VENGEFUL bot's grudge onto a rival of your choosing.
 */
enum class ArcId { GATHBANDHAN, AFWAAH, STING, BADLA }

/** Visual + voice register of a chat line — drives bubble tint/emphasis + speech styling in the UI. */
enum class MessageTone { NEUTRAL, FRIENDLY, SLY, HOSTILE, PANICKED, BOAST, SYSTEM }

/** What a chat line *is* — separates table-talk, private whispers, arc beats and the narrator. */
enum class ChatKind { TABLE, WHISPER, ARC, SYSTEM }

/**
 * One line in the Darbar (the table's running chat). Bots author these freely (proactively, and in
 * reaction to game events + the player's own messages); the player's own lines are folded in too.
 * Pure display data — it never feeds the engine.
 *
 * @property senderSeat raw seat of the speaker; `< 0` = the narrator ("Sutradhar").
 * @property targetSeat the seat the line is *about* / aimed at, if any (drives "@name" + caret).
 * @property fromPlayer true when this is the human's own line (right-aligned, distinct styling).
 */
data class ChatMessage(
    val id: Long,
    val senderSeat: Int,
    val targetSeat: Int? = null,
    val body: String,
    val tone: MessageTone = MessageTone.NEUTRAL,
    val kind: ChatKind = ChatKind.TABLE,
    val arc: ArcId? = null,
    val turn: Int = 0,
    val fromPlayer: Boolean = false,
) {
    val isNarrator: Boolean get() = senderSeat < 0
}

/** The shape of a thing the player can SAY — surfaced as a tappable quick-reply chip in the Darbar. */
enum class ChatActionKind { ARC_START, ARC_REPLY, TAUNT, PLACATE, DEFLECT, ALLY_PING }

/**
 * A tappable chat option offered to the player. Because the dialogue engine is template-driven
 * (offline + deterministic), the player drives the narrative by CHOOSING lines (chips) rather than
 * typing free prose — though [freeText] routing is also supported as a fallback.
 */
data class ChatSuggestion(
    val id: String,
    val label: String,
    val kind: ChatActionKind,
    val arc: ArcId? = null,
    val targetSeat: Int? = null,
    val targetName: String? = null,
    /** A one-line hint of what this does to the table (shown small under the chip). */
    val consequence: String? = null,
)

/**
 * The player's chat action — the ONLY external narrative input, and therefore the only thing that
 * must be LOGGED for deterministic resume (exactly like a human game [com.kursi.engine.Intent]). The
 * [SocialDirector] reconstructs the entire social fabric by replaying these in order.
 */
data class HumanChatInput(
    val suggestionId: String,
    val kind: ChatActionKind,
    val arc: ArcId? = null,
    val targetSeat: Int? = null,
    val freeText: String? = null,
)
