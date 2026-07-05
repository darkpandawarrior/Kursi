package com.kursi.feature.game

import com.kursi.engine.Intent
import com.kursi.feature.game.narrative.HumanChatInput
import com.kursi.feature.game.session.SnapChat
import com.kursi.feature.game.session.SnapIntent

/**
 * Sealed MVI action type for [GameViewModel].
 *
 * [Submit] wraps any concrete [Intent] the human player wants to submit to the engine.
 * [NewGame] starts a fresh game with the given seat count and difficulty level.
 */
sealed interface GameAction {
    /**
     * Submit an [intent] on behalf of the human player.
     * The ViewModel delegates to [session.GameSession.submitHuman].
     */
    data class Submit(
        val intent: Intent,
    ) : GameAction

    /**
     * Start (or restart) a game.
     *
     * @param playerCount   Total seat count (2..10).
     * @param difficulty    Bot difficulty: Easy / Medium / Hard / Expert.
     * @param seed          Optional deterministic seed; null → random.
     * @param resumeLog     M3 resume — when non-null, the persisted human action log is replayed
     *                      onto a fresh deterministic state instead of starting from move zero.
     * @param humanCount    M5 pass-and-play — number of human (hot-seat) players. 1 = vs-AI (default).
     *                      Humans take the lowest seats (0 .. humanCount-1); bots fill the rest.
     * @param teamCount     M6e TEAM KHEL — number of teams to split the table into (last-team-standing).
     *                      < 2 (default) = classic free-for-all. >= 2 alternates seats into that many teams.
     * @param spectator     M6e TAMASHA / DEMO — when true, the advisor auto-plays the human seat(s) too,
     *                      so the whole game unfolds on the real table as a watch-only demo.
     */
    data class NewGame(
        val playerCount: Int,
        val difficulty: Difficulty = Difficulty.Medium,
        val seed: Long? = null,
        /** The human player's display name — shown in moments, recap rail, and chat. */
        val playerName: String = "Khiladi",
        val resumeLog: List<SnapIntent>? = null,
        val humanCount: Int = 1,
        val teamCount: Int = 0,
        val spectator: Boolean = false,
        /** DARBAR — enable bot chat + the four story arcs + social targeting nudges. */
        val narrativeEnabled: Boolean = false,
        /** Optional story-arc id the player chose to lead with on the Story screen (e.g. "AFWAAH"). */
        val storyArc: String? = null,
        /** M3 resume — the persisted Darbar chat log, replayed in lock-step with [resumeLog]. */
        val resumeChat: List<SnapChat>? = null,
        /** DRAFT variant — a hand-picked deck role set ([com.kursi.engine.Role]); null = classic scaling. */
        val draftRoles: List<com.kursi.engine.Role>? = null,
        /** ANARCHY variant — the Khela cost falls over the game. */
        val anarchy: Boolean = false,
        /** BAIL PE BAHAR — allow spending 9 coins to restore one revealed influence to face-down. */
        val bailEnabled: Boolean = false,
        /** BALI KHEL (Sabotage) — allow voluntarily sacrificing one influence to gain 3 coins. */
        val sabotageEnabled: Boolean = false,
        /** HAWALA — allow gifting up to 5 coins directly to any alive opponent. */
        val hawalaEnabled: Boolean = false,
        /** ADHYADESH (Emergency) — when 25 lifetime coins earned, declare mass-Coup paying all coins. */
        val emergencyEnabled: Boolean = false,
        /** KHAZANA RAJ — first to accumulate [khazanaTarget] lifetime coins wins instead of last-standing. */
        val khazanaEnabled: Boolean = false,
        val khazanaTarget: Int = 25,
        /** MEHENGAI (Inflation) — all coin costs increase every few turns. */
        val inflationEnabled: Boolean = false,
        /** TANGI (Scarcity) — total coin pool capped (hoarding and denial strategies dominate). */
        val scarcityEnabled: Boolean = false,
    ) : GameAction

    /**
     * DARBAR — the human says something in chat (taps an arc/table-talk chip). Drives the social
     * fabric + story arcs and is logged for deterministic resume.
     */
    data class SendChat(
        val input: HumanChatInput,
    ) : GameAction

    /** DARBAR — the player opened the Darbar; clears the unread badge. */
    data object MarkChatRead : GameAction

    /**
     * M5 ASSISTANT — play the single BEST move ([com.kursi.ai.advisor.MoveAdvisor.bestMove]) for the
     * human's current decision. The ViewModel resolves it off-thread and submits it like a normal tap.
     * A no-op if it is not a human's turn.
     */
    data object PlayBestMove : GameAction
}

enum class Difficulty { Easy, Medium, Hard, Expert, Grandmaster }
