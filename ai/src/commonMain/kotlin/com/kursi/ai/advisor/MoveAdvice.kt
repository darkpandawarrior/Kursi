package com.kursi.ai.advisor

import com.kursi.engine.Intent

/**
 * A fully-evaluated advice entry for one legal [Intent] available to the human player.
 *
 * The UI consumes this directly:
 *   - Decision-coach: renders the ranked list with [rationale] and risk indicators.
 *   - Best-move highlight: tints the top-ranked button (where [recommended] == true).
 *   - AI assistant: streams [rationale] as in-character text.
 *   - Auto-mode: calls [MoveAdvisor.bestMove] which returns [intent] of the recommended entry.
 *
 * All fields are pure data — no Compose/IO dependencies.
 */
data class MoveAdvice(
    /** The intent this entry describes. */
    val intent: Intent,

    /**
     * Short human-readable label for the move, e.g.
     * "Tax (NETA)", "Block as VAKIL", "Challenge", "Pass".
     */
    val label: String,

    /**
     * Estimated win-probability for the human if they play this move, in [0, 1].
     * Derived from ISMCTS mean reward (visit-weighted rollout + static eval).
     */
    val winProb: Double,

    /**
     * True for exactly one entry in a [List<MoveAdvice>]: the single highest-ranked move
     * (ties broken by visit share). This is the move auto-mode will play.
     */
    val recommended: Boolean,

    /**
     * Whether the human actually holds the role this move claims.
     *  - Non-null for [Intent.DeclareAction] of a role-action (Tax/Assassinate/Steal/Exchange)
     *    and for [Intent.Block].
     *  - Null for moves that make no role claim (Income, ForeignAid, Coup, Challenge, Pass,
     *    ChooseInfluenceToLose, ChooseExchange).
     */
    val truthful: Boolean?,

    /**
     * True when this move claims a role the human does NOT hold (i.e. a bluff).
     * Always false when [truthful] is null or true.
     */
    val bluff: Boolean,

    /**
     * Contextual odds for high-stakes decisions:
     *  - For [Intent.Challenge]: P(opponent is bluffing) — challenge is +EV when this is high.
     *  - For a bluff [Intent.Block] or bluff [Intent.DeclareAction]: approximate
     *    P(not challenged and caught) ≈ P(no challenge) (rough; derived from BluffOdds).
     *  - Null for all other moves.
     */
    val successOdds: Double?,

    /**
     * One short, neutral-toned sentence that explains the move.
     *
     * Examples:
     *  - "You hold VAKIL — this block is real and safe."
     *  - "Bluff: you don't hold BHAI; risky if challenged."
     *  - "~65% they're bluffing — challenge is favourable."
     *  - "Guaranteed +1 coin, no risk."
     */
    val rationale: String,
)
