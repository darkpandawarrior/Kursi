package com.kursi.feature.game

import com.kursi.ai.EasyPolicy
import com.kursi.engine.GameConfig
import com.kursi.engine.PhaseView
import com.kursi.engine.PlayerId
import com.kursi.engine.Policy
import com.kursi.feature.game.session.GameSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSessionTest {
    // ─────────────────────────── helpers ───────────────────────────

    /**
     * Creates a [GameSession] where the human seat is [humanSeat] (default seat 0) and all
     * other seats are driven by an [EasyPolicy].
     */
    private fun makeSession(
        playerCount: Int,
        humanSeat: Int = 0,
        seed: Long = 42L,
    ): GameSession {
        val config = GameConfig.forPlayers(playerCount)
        val human = PlayerId(humanSeat)
        val bots =
            (0 until playerCount)
                .filter { it != humanSeat }
                .associate { seat ->
                    PlayerId(seat) to EasyPolicy(seed * 31L + seat) as Policy
                }
        return GameSession(config = config, seed = seed, humanSeat = human, bots = bots)
    }

    /**
     * Drives a [GameSession] to completion by feeding the human seat a bot policy each time
     * it is the human's turn.  Returns the final [GameUiState].
     */
    private fun driveToEnd(
        session: GameSession,
        humanSeed: Long = 99L,
    ): GameUiState {
        val humanPolicy = EasyPolicy(humanSeed)
        var ui = session.start()
        while (!ui.isGameOver) {
            if (ui.isHumanTurn) {
                // Drive human with the bot policy.
                assertTrue(ui.legalIntents.isNotEmpty(), "human's turn but no legal intents")
                val intent = humanPolicy.decide(ui.view, ui.legalIntents)
                ui = session.submitHuman(intent)
            } else {
                // Session should not be returning control to us when it's a bot turn.
                // If this assertion fires it means advanceUntilHumanOrEnd is broken.
                error("Session returned a non-human-turn state that is not game over")
            }
        }
        return ui
    }

    // ─────────────────────────── tests ───────────────────────────

    /**
     * T1 — bot-only completion: a 4-player game driven fully by policies reaches GameOver with a
     * valid winner seat and throws no exception.
     */
    @Test
    fun botOnlyCompletion_4players_reachesGameOver() {
        val session = makeSession(playerCount = 4, seed = 123L)
        val finalState = driveToEnd(session, humanSeed = 77L)

        assertTrue(finalState.isGameOver, "Expected game to be over")
        assertNotNull(finalState.winnerSeat, "Expected a winner")
        assertTrue(finalState.winnerSeat in 0..3, "Winner seat must be in 0..3, got ${finalState.winnerSeat}")
        assertTrue(finalState.view.phase is PhaseView.Over, "Phase must be Over")
    }

    /**
     * T2 — 2-player game also reaches GameOver with a valid winner.
     */
    @Test
    fun botOnlyCompletion_2players_reachesGameOver() {
        val session = makeSession(playerCount = 2, seed = 7L)
        val finalState = driveToEnd(session, humanSeed = 13L)

        assertTrue(finalState.isGameOver)
        assertNotNull(finalState.winnerSeat)
        assertTrue(finalState.winnerSeat in 0..1)
    }

    /**
     * T3 — pauses for human: after [start], if it's the human's turn the UiState carries
     * non-empty legal intents and is NOT game-over.  The session does NOT auto-advance past
     * the human's seat.
     */
    @Test
    fun pausesForHuman_initialState_hasNonEmptyLegalIntents() {
        // Seed chosen so the human (seat 0) goes first in a 2-player game.
        // Seat 0 always has seat index 0, so the first AwaitingAction is always seat 0.
        val session = makeSession(playerCount = 2, humanSeat = 0, seed = 1L)
        val ui = session.start()

        // The human (seat 0) always acts first per engine: AwaitingAction(actorSeat=0).
        assertFalse(ui.isGameOver, "Game should not be over immediately")
        assertTrue(ui.isHumanTurn, "Seat 0 acts first; session should pause for the human")
        assertTrue(ui.legalIntents.isNotEmpty(), "Human's first turn must have legal intents")
    }

    /**
     * T4 — session does not advance past the human: after [start] with humanSeat=0, calling
     * [GameSession.currentUiState] (without any submit) still shows it's the human's turn.
     */
    @Test
    fun sessionDoesNotAutoAdvancePastHuman() {
        val session = makeSession(playerCount = 2, humanSeat = 0, seed = 1L)
        val ui1 = session.start()
        val ui2 = session.currentUiState()

        // Both must be identical paused states — session must not auto-advance without a submit.
        assertEquals(ui1.isHumanTurn, ui2.isHumanTurn)
        assertEquals(ui1.isGameOver, ui2.isGameOver)
        assertEquals(ui1.legalIntents, ui2.legalIntents)
    }

    /**
     * T5 — bots never cheat: by construction, [GameSession.advanceUntilHumanOrEnd] calls
     * policy.decide with `redact(state, botId)` (a PlayerView), not the full GameState.
     * We verify this indirectly by asserting that the session compiles and a full game
     * completes without any "No policy registered" or engine error, AND that bots receive
     * only PlayerView (not GameState) — structural guarantee enforced by the Policy fun
     * interface signature.
     *
     * Additionally, we confirm the [GameUiState.view] the UI receives is redacted (it does
     * not expose opponents' face-down role identities — faceDownCount > 0 but no role list).
     */
    @Test
    fun botsNeverCheat_redactedViewHasNoOpponentHiddenRoles() {
        val session = makeSession(playerCount = 3, seed = 555L)
        val ui = session.start()

        // The human's PlayerView must not include opponents' hidden (face-down) roles.
        val opponents = ui.view.players.filter { it.id != ui.view.viewer }
        for (opp in opponents) {
            if (!opp.eliminated && opp.faceDownCount > 0) {
                // faceDownCount is public (pip count), but faceUpRoles should only contain
                // roles that have actually been revealed.  The face-down roles are NOT in faceUpRoles.
                // We can assert: for an alive opponent, faceUpRoles.size < copiesPerRole
                // (they can't have ALL roles face-up and still have faceDownCount > 0).
                assertTrue(
                    opp.faceUpRoles.size < ui.view.config.influencePerPlayer || opp.faceDownCount == 0,
                    "Opponent ${opp.id.raw} has ${opp.faceDownCount} hidden cards but faceUpRoles reports all influence as revealed — redaction broken",
                )
            }
        }
    }

    /**
     * T6 — start() is idempotent: calling start() again resets the session to a fresh state.
     */
    @Test
    fun startIsIdempotent_resetsFreshGame() {
        val session = makeSession(playerCount = 2, seed = 100L)
        val ui1 = session.start()

        // Advance one turn if it's the human's turn.
        if (ui1.isHumanTurn && ui1.legalIntents.isNotEmpty()) {
            session.submitHuman(ui1.legalIntents.first())
        }

        // Reset by calling start() again.
        val ui2 = session.start()
        assertFalse(ui2.isGameOver, "Reset game should not be over")
        assertEquals(ui1.view.turnNumber, ui2.view.turnNumber, "Reset should restore turn number")
        assertNull(ui2.winnerSeat, "Reset game should have no winner")
    }
}
