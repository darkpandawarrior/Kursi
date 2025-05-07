package com.kursi.feature.game

import com.kursi.core.network.ConnectionState
import com.kursi.core.network.OnlineUiState
import com.kursi.engine.Action
import com.kursi.engine.Intent
import com.kursi.engine.PhaseView
import com.kursi.engine.PlayerId
import com.kursi.protocol.wire.WireGameConfig
import com.kursi.protocol.wire.WireIntent
import com.kursi.protocol.wire.WireOpponentView
import com.kursi.protocol.wire.WireOwnCard
import com.kursi.protocol.wire.WirePhaseView
import com.kursi.protocol.wire.WirePlayerView
import com.kursi.protocol.wire.WireRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the PURE online → [GameUiState]/status mapping that the [OnlineGameAdapter] pumps into
 * StateFlows. Builds wire-typed [OnlineUiState] fixtures directly (no live client) and asserts the screen
 * sees correct engine-typed state, synthesized personas, and the always-legible connection status.
 */
class OnlineGameMappingTest {

    private val cfg2 = WireGameConfig(
        seatCount = 2, copiesPerRole = 3, roleCount = 5, influencePerPlayer = 2,
        startingCoins = 2, coupCost = 7, assassinateCost = 3, taxAmount = 3, foreignAidAmount = 2,
        stealAmount = 2, incomeAmount = 1, exchangeDrawCount = 2, forcedCoupThreshold = 10, coinSupply = 70,
    )

    private fun viewForSeat0OnTurn(): WirePlayerView = WirePlayerView(
        viewer = 0,
        config = cfg2,
        treasury = 60,
        deckCount = 11,
        turnNumber = 1,
        myCoins = 2,
        myInfluence = listOf(WireRole.NETA, WireRole.BHAI),
        myFaceUp = emptyList(),
        myCards = listOf(
            WireOwnCard(id = 0, role = WireRole.NETA, faceUp = false),
            WireOwnCard(id = 1, role = WireRole.BHAI, faceUp = false),
        ),
        players = listOf(
            WireOpponentView(id = 0, seatIndex = 0, coins = 2, faceUpRoles = emptyList(), faceDownCount = 2, eliminated = false),
            WireOpponentView(id = 1, seatIndex = 1, coins = 2, faceUpRoles = emptyList(), faceDownCount = 2, eliminated = false),
        ),
        phase = WirePhaseView.Turn(actor = 0),
    )

    @Test
    fun `maps connected in-game state to a renderable GameUiState with own hand and engine intents`() {
        val online = OnlineUiState(
            view = viewForSeat0OnTurn(),
            legalIntents = listOf(
                WireIntent.DeclareAction(0, com.kursi.protocol.wire.WireAction.Income),
                WireIntent.DeclareAction(0, com.kursi.protocol.wire.WireAction.Tax),
            ),
            recentEvents = emptyList(),
            isHumanTurn = true,
            isGameOver = false,
            winnerSeat = null,
            mySeat = 0,
            connection = ConnectionState.Connected(seat = 0),
        )

        val ui = online.toGameUiStateOrNull()
        assertTrue(ui != null, "a connected in-game state must produce a GameUiState")
        // Engine view re-hydrated: my own secret hand is present and the phase is my Turn.
        assertEquals(PlayerId(0), ui.view.viewer)
        assertEquals(2, ui.view.myCards.count { !it.faceUp }, "own face-down hand re-hydrated")
        assertTrue(ui.view.phase is PhaseView.Turn)
        assertTrue(ui.isHumanTurn)
        // Wire intents re-hydrated to engine Intents the screen dispatches.
        assertTrue(ui.legalIntents.any { it is Intent.DeclareAction && it.action == Action.Income })
        // Opponent (seat 1) gets a synthesized persona; my own seat 0 does NOT.
        assertEquals(setOf(PlayerId(1)), ui.opponentPersonas.keys)
        assertEquals("Seat 2", ui.opponentPersonas[PlayerId(1)]?.name)
        // Online table is never pass-and-play and carries no coach data.
        assertFalse(ui.isPassAndPlay)
        assertTrue(ui.advice.isEmpty())
        assertEquals(0, ui.activeSeat, "active seat is my seat on my turn")
    }

    @Test
    fun `no view yet yields a null GameUiState so the table is not blanked`() {
        val online = OnlineUiState(view = null, connection = ConnectionState.Connecting)
        assertNull(online.toGameUiStateOrNull())
    }

    @Test
    fun `connection status is always-legible across the lifecycle`() {
        // Connecting.
        assertEquals(
            OnlineConnectionStatus.Connecting,
            OnlineUiState(connection = ConnectionState.Connecting).toConnectionStatus(),
        )
        // Connected but no view ⇒ waiting for players.
        assertEquals(
            OnlineConnectionStatus.WaitingForPlayers,
            OnlineUiState(view = null, connection = ConnectionState.Connected(0)).toConnectionStatus(),
        )
        // Connected with a view ⇒ in game (inputs enabled).
        val inGame = OnlineUiState(view = viewForSeat0OnTurn(), connection = ConnectionState.Connected(0))
            .toConnectionStatus()
        assertEquals(OnlineConnectionStatus.InGame, inGame)
        assertTrue(inGame.inputsEnabled)
        // Reconnecting carries the attempt and disables inputs.
        val recon = OnlineUiState(connection = ConnectionState.Reconnecting(attempt = 3)).toConnectionStatus()
        assertEquals(OnlineConnectionStatus.Reconnecting(3), recon)
        assertFalse(recon.inputsEnabled)
        // Dropped ⇒ server lost (terminal, inputs disabled).
        val lost = OnlineUiState(connection = ConnectionState.Dropped(cause = "socket closed")).toConnectionStatus()
        assertTrue(lost is OnlineConnectionStatus.ServerLost)
        assertFalse(lost.inputsEnabled)
        // Closed AFTER game over keeps showing the final table.
        assertEquals(
            OnlineConnectionStatus.InGame,
            OnlineUiState(isGameOver = true, connection = ConnectionState.Closed).toConnectionStatus(),
        )
    }

    @Test
    fun `game over maps winner and turns inputs off`() {
        val view = viewForSeat0OnTurn().copy(phase = WirePhaseView.Over(winner = 0))
        val online = OnlineUiState(
            view = view,
            legalIntents = emptyList(),
            isHumanTurn = false,
            isGameOver = true,
            winnerSeat = 0,
            mySeat = 0,
            connection = ConnectionState.Closed,
        )
        val ui = online.toGameUiStateOrNull()!!
        assertTrue(ui.isGameOver)
        assertEquals(0, ui.winnerSeat)
        assertFalse(ui.isHumanTurn)
    }
}
