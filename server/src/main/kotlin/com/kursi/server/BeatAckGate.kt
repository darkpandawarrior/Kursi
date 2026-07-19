package com.kursi.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bounded wait for a human "continue" ack after a meaningful beat — the server-side mirror of offline
 * [com.kursi.feature.game.GameViewModel.awaitBeat]'s FOCUS/GUIDED tap-to-continue gate (see
 * `feature/game/BeatGate.kt`). [MatchActor] arms the gate after broadcasting a non-trivial event batch
 * whose next actor is bot-driven, holding off the bot cascade so connected humans get a moment to read
 * what just happened; [MatchCommand.BeatAckReceived] (client [com.kursi.protocol.wire.ClientMessage.ContinueBeat])
 * releases it early, and the bounded timeout releases it otherwise — so one idle/disconnected player can
 * never freeze the table.
 *
 * [onTimeout] fires on [scope] when the wait elapses with no ack. It must be non-suspending and side-effect
 * FREE of directly mutating actor state — [MatchActor] uses it only to post [MatchCommand.BeatTimedOut] back
 * into its own mailbox, so the actual state mutation stays confined to the actor's single serial loop (no
 * locks, no races — same invariant [MatchActor] documents for itself).
 *
 * @param scope drives the bounded timer. Inject a `TestScope`/virtual-time dispatcher in tests for a
 *   deterministic, sleep-free timeout.
 */
class BeatAckGate(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = BEAT_ACK_TIMEOUT_MS,
    private val onTimeout: () -> Unit,
) {
    private var job: Job? = null

    /** True while a beat wait is outstanding (armed and not yet acked or timed out). */
    val isPending: Boolean get() = job != null

    /** Arms a fresh bounded wait, replacing any wait already in flight. */
    fun arm() {
        cancel()
        job =
            scope.launch {
                delay(timeoutMs)
                job = null
                onTimeout()
            }
    }

    /** Cancels any pending wait without firing [onTimeout] — used by an early ack or a state change
     *  (join/reconnect/leave) that makes the pending beat moot. No-op if nothing is pending. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    companion object {
        /** How long the table holds a meaningful beat for a human ack before auto-continuing. */
        const val BEAT_ACK_TIMEOUT_MS: Long = 4_000L
    }
}
