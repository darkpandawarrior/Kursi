package com.kursi.feature.game

import com.kursi.core.network.ConnectionState
import com.kursi.core.network.OnlineUiState
import com.kursi.core.network.toEngineProjection
import com.kursi.engine.PlayerId

/**
 * PURE, side-effect-free mapping from the wire-typed [OnlineUiState] to the [GameUiState] / status the
 * offline screen renders. Extracted from [OnlineGameAdapter] so it is unit-testable without a live
 * client or coroutines: the adapter is just the thin lifecycle shell that pumps these into StateFlows.
 */

/**
 * The deterministic per-seat seat tints (ARGB Long), Okabe-Ito, keyed by seat index so the same
 * opponent renders the same colour across reconnects. Colour-blind-safe.
 */
internal val ONLINE_SEAT_COLORS: LongArray = longArrayOf(
    0xFF0072B2L, // blue
    0xFFD55E00L, // vermillion
    0xFF009E73L, // bluish green
    0xFFCC79A7L, // reddish purple
    0xFF56B4E9L, // sky blue
    0xFFE69F00L, // orange
    0xFFF0E442L, // yellow
    0xFF999999L, // grey
    0xFF000000L, // black
    0xFFFFFFFFL, // white
)

/**
 * Map [OnlineUiState] → the [GameUiState] the [GameScreen] renders, or null when there is no view yet
 * (still connecting / waiting — the caller keeps the last shown frame and lets the status banner speak).
 *
 * Re-hydrates the wire view/intents/events into engine types via [OnlineUiState.toEngineProjection] and
 * dresses them with the online-specific fields:
 *  - [GameUiState.opponentPersonas]: synthesized "Seat N" labels (the wire has no names);
 *  - coach surfaces ([GameUiState.advice] / [GameUiState.opponentInsights]) left empty (out of scope);
 *  - [GameUiState.isPassAndPlay] = false (online is one human per client);
 *  - [GameUiState.activeSeat] = my seat on my turn (the screen's turn affordance).
 */
internal fun OnlineUiState.toGameUiStateOrNull(): GameUiState? {
    val proj = toEngineProjection()
    val view = proj.view ?: return null
    return GameUiState(
        view = view,
        legalIntents = proj.legalIntents,
        recentEvents = proj.recentEvents,
        isHumanTurn = proj.isHumanTurn,
        isGameOver = proj.isGameOver,
        winnerSeat = proj.winnerSeat,
        opponentPersonas = synthSeatPersonas(view.players.map { it.id }, mySeat = proj.mySeat),
        advice = emptyList(),
        opponentInsights = emptyList(),
        coachEnabled = true,
        activeSeat = if (proj.isHumanTurn) proj.mySeat else null,
        isPassAndPlay = false,
    )
}

/**
 * Map the raw [OnlineUiState] to the ALWAYS-LEGIBLE [OnlineConnectionStatus] banner state.
 * See [OnlineConnectionStatus] for the full transition table and why [OnlineConnectionStatus.OpponentDisconnected]
 * is reserved (the current server bot-fills silently).
 */
internal fun OnlineUiState.toConnectionStatus(): OnlineConnectionStatus = when (val c = connection) {
    is ConnectionState.Idle -> OnlineConnectionStatus.Connecting
    is ConnectionState.Connecting -> OnlineConnectionStatus.Connecting
    is ConnectionState.Reconnecting -> OnlineConnectionStatus.Reconnecting(c.attempt)
    is ConnectionState.Dropped -> OnlineConnectionStatus.ServerLost(c.cause)
    is ConnectionState.Closed ->
        // A game-over close keeps showing the (final) table; a caller/forced close is a lost connection.
        if (isGameOver) OnlineConnectionStatus.InGame else OnlineConnectionStatus.ServerLost(cause = null)
    is ConnectionState.Connected ->
        // Connected but no first StateUpdate yet ⇒ the room hasn't started (seats not full).
        if (view == null) OnlineConnectionStatus.WaitingForPlayers else OnlineConnectionStatus.InGame
}

/**
 * Build per-seat [OpponentPersona]s from seat indices alone (the wire carries no names). The local
 * [mySeat] is omitted (the screen renders it as the human, not an opponent).
 */
internal fun synthSeatPersonas(seats: List<PlayerId>, mySeat: Int?): Map<PlayerId, OpponentPersona> =
    seats.filter { it.raw != mySeat }.associateWith { id ->
        OpponentPersona(
            playerId = id,
            name = "Seat ${id.raw + 1}",
            monogram = "S${id.raw + 1}",
            seatColorArgb = ONLINE_SEAT_COLORS[id.raw % ONLINE_SEAT_COLORS.size],
        )
    }
