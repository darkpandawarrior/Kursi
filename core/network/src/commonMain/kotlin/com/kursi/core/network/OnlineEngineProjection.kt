package com.kursi.core.network

import com.kursi.engine.GameEvent
import com.kursi.engine.Intent
import com.kursi.engine.PlayerView
import com.kursi.protocol.wire.toEngine

/**
 * The engine-typed projection of an [OnlineUiState] — the exact fields the offline `GameScreen`
 * renders, reconstructed from the redacted wire state the server sent this seat.
 *
 * This is the SOURCE-NEUTRAL core of the online bridge: it lives in :core:network (no Compose, no
 * :feature:game, no :ai), so it is reachable from the :server integration test, which proves the
 * client reconstructs correct REDACTED engine state end-to-end against a real server. The thin
 * `feature.game` adapter then wraps this into the full `GameUiState` (adding personas/coach/etc.).
 *
 * [view] is null until the first `StateUpdate` arrives (during connecting), mirroring [OnlineUiState.view].
 */
data class OnlineEngineProjection(
    /** The redacted engine view this seat is entitled to render, or null before the first StateUpdate. */
    val view: PlayerView?,
    /** Concrete legal engine intents for this seat's current turn (empty when not this seat's turn). */
    val legalIntents: List<Intent>,
    /** Recent engine events for the history panel (most-recent last). */
    val recentEvents: List<GameEvent>,
    /** True when it is currently THIS seat's turn to act. */
    val isHumanTurn: Boolean,
    /** True when the game has ended. */
    val isGameOver: Boolean,
    /** The winning seat index, or null while the game is still running. */
    val winnerSeat: Int?,
    /** This seat's index, or null before RoomJoined. */
    val mySeat: Int?,
    /** The live connection lifecycle. */
    val connection: ConnectionState,
    /** The most recent server rejection reason for this client, or null. */
    val lastError: String?,
)

/**
 * Projects the wire-typed [OnlineUiState] into engine types the offline screen consumes.
 *
 * - [OnlineUiState.view] → engine [PlayerView] via [WirePlayerView.toEngineView].
 * - [OnlineUiState.legalIntents] (already derived client-side per the server's redacted view) →
 *   engine [Intent] via the existing wire→engine intent mapper. NOTE: we map the SERVER-DERIVED
 *   intents straight across rather than re-deriving from the engine view, so the online legal-move
 *   set stays bit-identical to what `WirePlayerView.legalIntents()` produced (the single source of
 *   truth the server also re-validates against).
 * - [OnlineUiState.recentEvents] → engine [GameEvent] via [WireGameEvent.toEngineEvent].
 *
 * All connection/turn/over flags pass straight through.
 */
fun OnlineUiState.toEngineProjection(): OnlineEngineProjection = OnlineEngineProjection(
    view = view?.toEngineView(),
    legalIntents = legalIntents.map { it.toEngine() },
    recentEvents = recentEvents.map { it.toEngineEvent() },
    isHumanTurn = isHumanTurn,
    isGameOver = isGameOver,
    winnerSeat = winnerSeat,
    mySeat = mySeat,
    connection = connection,
    lastError = lastError,
)
