package com.kursi.feature.game

import com.kursi.engine.*

// ─────────────────────────── GamePhase ───────────────────────────

sealed interface GamePhase {
    object Idle : GamePhase

    object PickAction : GamePhase

    data class PickTarget(
        val action: Action,
    ) : GamePhase

    data class Confirm(
        val action: Action,
        val target: PlayerId?,
    ) : GamePhase

    data class ReactionWindow(
        val step: ReactionStep,
        val actor: PlayerId,
        val action: Action,
        val claimedRole: Role?,
        val blocker: PlayerId?,
        val blockRole: Role?,
        val myLegalResponses: List<Intent>,
    ) : GamePhase

    object LoseInfluence : GamePhase

    data class Exchange(
        val drawn: List<CardId>,
    ) : GamePhase

    /** Jaanch follow-up — the human examiner decides whether to force the target to redraw. */
    data class InvestigatePeek(
        val target: PlayerId,
        val peekedRole: Role?,
    ) : GamePhase

    data class GameOver(
        val winnerSeat: Int,
    ) : GamePhase
}
