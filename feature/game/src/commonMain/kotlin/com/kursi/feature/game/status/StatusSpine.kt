package com.kursi.feature.game.status

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.engine.*
import com.kursi.feature.game.*

// ─────────────────────────── WHAT-JUST-HAPPENED recap (Clarity, Tenet 1) ──────
// A compact, plain-language recap of the most-recent resolved beat, shown near the
// spine on EVERY phase. ALWAYS shown — NOT gated under the coach (this is
// comprehension, not advice). Routes all copy through KursiVoice (bilingual).

/** Returns true for events that merit a plain-language recap line. */
internal fun isRecapWorthy(ev: GameEvent): Boolean =
    when (ev) {
        is GameEvent.ActionDeclared, is GameEvent.Blocked, is GameEvent.Challenged,
        is GameEvent.ChallengeRevealed, is GameEvent.InfluenceLost,
        is GameEvent.PlayerEliminated, is GameEvent.CoinsTransferred,
        is GameEvent.Exchanged, is GameEvent.GameEnded,
        -> true
        else -> false
    }

/** The most recent event worth recapping in plain words (skips bookkeeping noise). */
internal fun mostRecentRecapEvent(state: GameUiState): GameEvent? = state.recentEvents.lastOrNull { isRecapWorthy(it) }

/** The last [n] recap-worthy events, oldest first. Used by IdleDock feed. */
internal fun lastNRecapEvents(
    state: GameUiState,
    n: Int,
): List<GameEvent> = state.recentEvents.filter { isRecapWorthy(it) }.takeLast(n)

/** Resolve the primary + secondary display names a recap line needs for [event]. */
internal fun recapNames(
    event: GameEvent,
    state: GameUiState,
): Pair<String, String?> {
    fun n(id: PlayerId) = personaNameOrDefault(id, state)
    return when (event) {
        is GameEvent.ActionDeclared -> n(event.actor) to null
        is GameEvent.Blocked -> n(event.blocker) to null
        is GameEvent.Challenged -> n(event.challenger) to n(event.target)
        is GameEvent.ChallengeRevealed -> n(event.player) to null
        is GameEvent.InfluenceLost -> n(event.player) to null
        is GameEvent.PlayerEliminated -> n(event.player) to null
        is GameEvent.CoinsTransferred -> n(event.from) to n(event.to)
        is GameEvent.Exchanged -> n(event.actor) to null
        is GameEvent.GameEnded -> n(event.winner) to null
        else -> "" to null
    }
}

/**
 * Top-of-screen recap strip — always a single compact line to preserve hand visibility.
 *
 * During PickAction with narrative mode on, swaps to show the last bot chat message so
 * the player can't miss what was said to them while bots were taking turns. Falls back
 * to the normal PICHLA DAV game-event recap when there's no chat. Full thread is in DARBAR.
 *
 * This MUST stay single-line: any vertical expansion here directly reduces the weight(1f)
 * middle Column, squishing YourHandPanel to zero height on small phones.
 */
@Composable
internal fun RecapRail(
    state: GameUiState,
    gamePhase: GamePhase,
    modifier: Modifier = Modifier,
) {
    val voice = LocalKursiVoice.current
    val isPickAction = gamePhase is GamePhase.PickAction

    // During the player's action phase in narrative mode, surface the last bot chat first —
    // the player may have been distracted during bot turns and missed what was said to them.
    val lastBotMsg =
        if (isPickAction && state.narrativeEnabled) {
            state.chatFeed.lastOrNull { !it.fromPlayer && !it.isNarrator }
        } else {
            null
        }

    if (lastBotMsg != null) {
        val senderPlayer = state.view.players.firstOrNull { it.seatIndex == lastBotMsg.senderSeat }
        val senderName = senderPlayer?.let { state.opponentPersonas[it.id]?.name } ?: "Bot"
        val chatLine = "\"${lastBotMsg.body}\""
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .clip(Squircle(KursiRadii.sm))
                    .background(BrandTokens.TeakDark.copy(alpha = 0.85f))
                    .border(
                        KursiDimens.stroke_hairline,
                        KursiNeutrals.TextMuted.copy(alpha = 0.6f),
                        Squircle(KursiRadii.sm),
                    ).padding(horizontal = KursiDimens.space_sm, vertical = 5.dp)
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Darbar — $senderName: ${lastBotMsg.body}"
                    },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_sm),
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(Squircle(KursiRadii.xs))
                        .background(KursiNeutrals.TextMuted.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "💬 $senderName",
                    style = KursiType.label_micro.copy(letterSpacing = 0.6.sp),
                    color = BrandTokens.GoldAntique,
                    maxLines = 1,
                )
            }
            Text(
                text = chatLine,
                style = KursiType.label_sm,
                color = KursiNeutrals.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    // No bot chat to surface — show the normal game-event recap.
    val event = mostRecentRecapEvent(state) ?: return
    val (actor, other) = recapNames(event, state)
    val line = voice.recap(event, actor, other) ?: return
    val label = if (isPickAction) "PICHLA DAV" else voice.recapLabel

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Squircle(KursiRadii.sm))
                .background(BrandTokens.TeakDark.copy(alpha = 0.85f))
                .border(
                    KursiDimens.stroke_hairline,
                    BrandTokens.BrassDark.copy(alpha = 0.5f),
                    Squircle(KursiRadii.sm),
                ).padding(horizontal = KursiDimens.space_sm, vertical = 5.dp)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "$label: $line"
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_sm),
    ) {
        Box(
            modifier =
                Modifier
                    .clip(Squircle(KursiRadii.xs))
                    .background(BrandTokens.BrassDark.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                style = KursiType.label_micro.copy(letterSpacing = 0.6.sp),
                color = BrandTokens.GoldAntique,
                maxLines = 1,
            )
        }
        Text(
            text = line,
            style = KursiType.label_sm,
            color = KursiNeutrals.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─────────────────────────── StatusSpine ───────────────────────────

@Composable
internal fun StatusSpineBar(
    state: GameUiState,
    gamePhase: GamePhase,
    modifier: Modifier = Modifier,
) {
    val humanSeat = state.view.viewer
    val voice = LocalKursiVoice.current
    val (text, tone) = deriveSpineTextAndTone(state, gamePhase, humanSeat, voice)
    // A11y (M3 §1): the spine is the running narration of the table — whose turn it is and what
    // just happened. Mark it a polite live region so a screen reader announces each change without
    // the player having to hunt for it, and carry the text as the spoken description.
    StatusSpine(
        text = text,
        tone = tone,
        modifier =
            modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
    )
}

internal fun deriveSpineTextAndTone(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    voice: KursiVoice,
): Pair<String, SpineTone> =
    when (gamePhase) {
        is GamePhase.Idle -> {
            val actorId =
                when (val phase = state.view.phase) {
                    is PhaseView.Turn -> phase.actor
                    is PhaseView.Reactions -> phase.actor
                    is PhaseView.InfluenceLoss -> phase.loser
                    is PhaseView.Exchange -> phase.actor
                    else -> null
                }
            val actorName = actorId?.let { personaNameOrDefault(it, state) } ?: actorName(state)
            voice.opponentActing(actorName) to SpineTone.Info
        }
        is GamePhase.PickAction -> {
            if (state.view.myCoins >= 10) {
                "${voice.forcedCoup} (${state.view.myCoins} coins)" to SpineTone.Danger
            } else {
                voice.yourTurn to SpineTone.Gold
            }
        }
        is GamePhase.PickTarget -> {
            val verb = targetVerb(gamePhase.action)
            val role = Rules.claimedRole(gamePhase.action)
            val roleStr = if (role != null) " (claiming ${roleLabel(role)})" else ""
            "${actionName(gamePhase.action)} — choose who to $verb.$roleStr" to SpineTone.Gold
        }
        is GamePhase.Confirm -> {
            val targetName = gamePhase.target?.let { personaNameOrDefault(it, state, voice.selfName) }
            val summary = confirmSummary(gamePhase.action, gamePhase.target, targetName)
            "$summary — Declare or Cancel." to SpineTone.Gold
        }
        is GamePhase.ReactionWindow -> {
            when (gamePhase.step) {
                ReactionStep.CHALLENGE_ACTION -> {
                    "${voice.challengePrompt} — ${actionName(gamePhase.action)}" to SpineTone.Pending
                }
                ReactionStep.BLOCK -> {
                    voice.blockPrompt to SpineTone.Pending
                }
                ReactionStep.CHALLENGE_BLOCK -> {
                    val blockerName = gamePhase.blocker?.let { personaNameOrDefault(it, state) } ?: "Someone"
                    "$blockerName blocks. ${voice.challengePrompt}" to SpineTone.Pending
                }
            }
        }
        is GamePhase.LoseInfluence -> {
            voice.loseInfluence to SpineTone.Danger
        }
        is GamePhase.Exchange -> {
            voice.exchange to SpineTone.Gold
        }
        is GamePhase.InvestigatePeek -> {
            val targetName = personaNameOrDefault(gamePhase.target, state)
            val seen = gamePhase.peekedRole?.let { " You saw ${roleLabel(it)}." } ?: ""
            "JAANCH — $targetName's card.$seen Spike it or leave it?" to SpineTone.Gold
        }
        is GamePhase.GameOver -> {
            val humanSeatIdx = humanSeat.raw
            if (gamePhase.winnerSeat == humanSeatIdx) {
                voice.youWin to SpineTone.Gold
            } else {
                val winnerName = personaNameOrDefault(com.kursi.engine.PlayerId(gamePhase.winnerSeat), state)
                voice.opponentWins(winnerName) to SpineTone.Info
            }
        }
    }
