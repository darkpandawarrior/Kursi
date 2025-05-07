package com.kursi.feature.game

/**
 * The ALWAYS-LEGIBLE online connection lifecycle the [GameScreen] surfaces as a status banner.
 *
 * This is the UI-facing projection of [com.kursi.core.network.ConnectionState] PLUS the match-readiness
 * facts the screen needs, collapsed into the small set of states a player must be able to read at a
 * glance. The tenet: the player ALWAYS knows (a) the connection state and (b) whose turn it is. Whose
 * turn lives in [GameUiState] (the engine view + isHumanTurn); the connection half lives here.
 *
 * Mapping from the network layer (see [OnlineGameAdapter.statusOf]):
 *   ConnectionState.Idle / Connecting           → [Connecting]
 *   ConnectionState.Connected, no view yet       → [WaitingForPlayers]   (room not full / not started)
 *   ConnectionState.Connected, view present      → [InGame]
 *   ConnectionState.Reconnecting(n)              → [Reconnecting]
 *   ConnectionState.Dropped(cause)               → [ServerLost]          (auto-reconnect exhausted/off)
 *   ConnectionState.Closed (game over)           → [InGame]              (final state still shown)
 *   ConnectionState.Closed (caller closed)       → [ServerLost]
 *
 * [OpponentDisconnected] is a DISTINCT, RESERVED state. The current server transparently bot-fills a
 * vacated seat and keeps broadcasting `StateUpdate`s WITHOUT signalling other clients, so this state is
 * not produced from the wire today — it is modelled now so the screen can render it the moment the
 * protocol gains an opponent-left signal, without a UI redesign. It is documented here as the contract
 * the Screens phase renders against. (My OWN drop surfaces as [Reconnecting] while the server bot-plays
 * my seat until I resume — that is already observable.)
 */
sealed interface OnlineConnectionStatus {

    /**
     * Whether inputs should be ENABLED. Only [InGame] permits play; every other state means the table is
     * either not live or not authoritative right now, so the screen disables action chips and shows the
     * banner. (Within [InGame] the per-turn gate is still `GameUiState.isHumanTurn`.)
     */
    val inputsEnabled: Boolean get() = this is InGame

    /** A short, human-legible label for the status banner. Localisation happens in the screen layer. */
    val label: String

    /** Socket opening / handshake in flight. Inputs disabled; "Connecting…". */
    data object Connecting : OnlineConnectionStatus {
        override val label: String get() = "Connecting…"
    }

    /** Connected and seated, but the room isn't full / the match hasn't started. "Waiting for players…". */
    data object WaitingForPlayers : OnlineConnectionStatus {
        override val label: String get() = "Waiting for players…"
    }

    /** Live, authoritative match. Inputs enabled (subject to the per-turn `isHumanTurn` gate). */
    data object InGame : OnlineConnectionStatus {
        override val label: String get() = "In game"
    }

    /**
     * The socket dropped and an auto-reconnect (resuming this seat) is in flight. The server bot-plays
     * this seat in the meantime; on resync the player gets their hand back. Inputs disabled.
     *
     * @param attempt 1-based retry counter, for an optional "Reconnecting… (2/6)" affordance.
     */
    data class Reconnecting(val attempt: Int) : OnlineConnectionStatus {
        override val label: String get() = "Reconnecting…"
    }

    /**
     * RESERVED: an OPPONENT dropped and the server is bot-filling their seat. Not produced by the
     * current protocol (the server bot-fills silently); modelled for forward-compatibility so the
     * screen can show "Opponent disconnected — bot is playing their seat" the moment a server signal
     * exists. Inputs stay ENABLED for the table is still live and authoritative — but rendered here as
     * a NON-blocking informational banner, so [inputsEnabled] is overridden true.
     *
     * @param seat the vacated opponent's seat index (for naming the banner), or null if unknown.
     */
    data class OpponentDisconnected(val seat: Int?) : OnlineConnectionStatus {
        override val inputsEnabled: Boolean get() = true
        override val label: String get() = "Opponent left — bot playing their seat"
    }

    /**
     * The connection is gone for good: auto-reconnect exhausted its budget, reconnect was disabled, or
     * the caller closed the client. Terminal from the table's perspective. Inputs disabled; the screen
     * offers a return-to-lobby / retry affordance.
     *
     * @param cause the underlying drop reason, if the network layer provided one.
     */
    data class ServerLost(val cause: String?) : OnlineConnectionStatus {
        override val label: String get() = "Connection lost"
    }
}
