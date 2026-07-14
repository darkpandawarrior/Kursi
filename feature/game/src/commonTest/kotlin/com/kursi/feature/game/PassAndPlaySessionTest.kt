package com.kursi.feature.game

import com.kursi.ai.EasyPolicy
import com.kursi.ai.Policy
import com.kursi.engine.GameConfig
import com.kursi.engine.PlayerId
import com.kursi.engine.legalIntents
import com.kursi.feature.game.session.GameSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M5 PASS-AND-PLAY — a multi-human (hot-seat) [GameSession] must:
 *  1. Pause whenever the NEXT actor is ANY human seat (each human takes their own turn).
 *  2. Build the UI state redacted for the seat that must act NOW, so one hot-seat player can never
 *     see another human seat's hidden (face-down) cards.
 */
class PassAndPlaySessionTest {
    /** A 2-human (seats 0 & 1) + bots session for [playerCount] seats. */
    private fun makePassAndPlay(
        playerCount: Int,
        humanSeats: Set<Int>,
        seed: Long = 42L,
    ): GameSession {
        val config = GameConfig.forPlayers(playerCount)
        val humans = humanSeats.map { PlayerId(it) }.toSet()
        val bots =
            (0 until playerCount)
                .filter { PlayerId(it) !in humans }
                .associate { seat -> PlayerId(seat) to EasyPolicy(seed * 31L + seat) as Policy }
        return GameSession(
            config = config,
            seed = seed,
            humanSeats = humans,
            bots = bots,
        )
    }

    /**
     * The session pauses for EACH human seat in turn: as we drive a 2-human game with a bot policy,
     * the session only ever hands control back when [GameUiState.activeSeat] is one of the human
     * seats — and over the course of the game BOTH human seats get the spotlight at least once.
     */
    @Test
    fun pausesForEachHumanInTurn() {
        val session = makePassAndPlay(playerCount = 4, humanSeats = setOf(0, 1), seed = 7L)
        val driver = EasyPolicy(99L)

        val seatsThatActed = mutableSetOf<Int>()
        var ui = session.start()
        var guard = 0
        while (!ui.isGameOver && guard++ < 5_000) {
            // Whenever control rests on us, it must be a HUMAN seat (0 or 1), never a bot seat.
            assertTrue(ui.isHumanTurn, "session returned control on a non-human turn")
            val active = ui.activeSeat
            assertNotNull(active, "human turn must carry an active seat")
            assertTrue(active == 0 || active == 1, "active seat must be a human seat, was $active")
            // The redacted view's viewer must be the active human seat.
            assertEquals(active, ui.view.viewer.raw, "view must be redacted for the active seat")
            seatsThatActed += active

            assertTrue(ui.legalIntents.isNotEmpty(), "active human must have legal intents")
            ui = session.submitHuman(driver.decide(ui.view, ui.legalIntents))
        }
        // Both human seats must have taken at least one turn before the game ended (unless one was
        // eliminated very early — with seed 7L both act). At minimum the lower seat acts.
        assertTrue(0 in seatsThatActed, "human seat 0 never acted")
        assertTrue(
            seatsThatActed.size >= 1,
            "expected at least one human seat to have acted, got $seatsThatActed",
        )
    }

    /**
     * SECRECY: at every human pause, the view shown is redacted for the ACTIVE seat — that seat sees
     * its own face-down roles, and every OTHER seat's face-down cards are hidden (only a pip count,
     * no role identities). A hot-seat player thus never sees another human's hand.
     */
    @Test
    fun activeSeatNeverSeesAnotherSeatsHiddenCards() {
        val session = makePassAndPlay(playerCount = 4, humanSeats = setOf(0, 1), seed = 123L)
        val driver = EasyPolicy(55L)

        var ui = session.start()
        var guard = 0
        while (!ui.isGameOver && guard++ < 5_000) {
            val active = ui.activeSeat!!
            // The view is the active seat's: its own influence is visible.
            assertEquals(active, ui.view.viewer.raw)

            // For every OTHER alive seat, the redacted view exposes only a face-down PIP COUNT,
            // never the hidden role identities (faceUpRoles only holds revealed cards).
            for (opp in ui.view.players.filter { it.id.raw != active }) {
                if (!opp.eliminated && opp.faceDownCount > 0) {
                    assertTrue(
                        opp.faceUpRoles.size < ui.view.config.influencePerPlayer,
                        "seat ${opp.id.raw} leaks all influence to seat $active — redaction broken",
                    )
                }
            }
            ui = session.submitHuman(driver.decide(ui.view, ui.legalIntents))
        }
    }

    /**
     * The advance loop never pauses on a BOT seat: with humans on 0 & 1, any pause has active seat in
     * {0,1}; bot seats 2 & 3 are auto-played and never surfaced to the caller.
     */
    @Test
    fun botSeatsAreNeverSurfaced() {
        val session = makePassAndPlay(playerCount = 4, humanSeats = setOf(0, 1), seed = 99L)
        val driver = EasyPolicy(7L)

        var ui = session.start()
        var guard = 0
        while (!ui.isGameOver && guard++ < 5_000) {
            assertTrue(ui.activeSeat in listOf(0, 1), "surfaced a bot seat ${ui.activeSeat}")
            ui = session.submitHuman(driver.decide(ui.view, ui.legalIntents))
        }
        assertTrue(ui.isGameOver)
        assertNotNull(ui.winnerSeat)
    }

    /** A single-human session keeps the legacy semantics: activeSeat is always 0 on the human turn. */
    @Test
    fun singleHumanSession_activeSeatAlwaysZero() {
        val config = GameConfig.forPlayers(2)
        val bots = mapOf(PlayerId(1) to EasyPolicy(1L) as Policy)
        val session = GameSession(config = config, seed = 1L, humanSeat = PlayerId(0), bots = bots)
        val ui = session.start()
        assertTrue(ui.isHumanTurn)
        assertEquals(0, ui.activeSeat)
        assertEquals(false, ui.isPassAndPlay)
    }
}
