package com.kursi.feature.game

import com.kursi.engine.GameEvent

/** Rhythm tier of a resolved beat. Drives pacing (delay length) AND gating (which beats wait for a tap). */
enum class BeatTier { TRIVIAL, ROUTINE, DRAMATIC }

/** A resolved beat the paced loop is holding on until the player taps to continue (FOCUS/GUIDED only). */
data class PendingBeat(
    val tier: BeatTier,
)

/**
 * Classify a batch of events into a [BeatTier]. Single source of truth for both the timed pacing
 * ([GameViewModel.pauseFor]) and the beat gate ([GameViewModel.awaitBeat]).
 */
fun tierFor(events: List<GameEvent>): BeatTier {
    val dramatic =
        events.any {
            it is GameEvent.Challenged ||
                it is GameEvent.ChallengeRevealed ||
                it is GameEvent.Blocked ||
                it is GameEvent.InfluenceLost ||
                it is GameEvent.PlayerEliminated ||
                it is GameEvent.GameEnded
        }
    if (dramatic) return BeatTier.DRAMATIC
    val routine =
        events.any {
            it is GameEvent.ActionDeclared ||
                it is GameEvent.ActionResolved ||
                it is GameEvent.ActionNegated ||
                it is GameEvent.Exchanged ||
                it is GameEvent.CoinsTransferred
        }
    return if (routine) BeatTier.ROUTINE else BeatTier.TRIVIAL
}
