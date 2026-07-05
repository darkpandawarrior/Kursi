package com.kursi.feature.game

import com.kursi.ai.EasyPolicy
import com.kursi.engine.GameConfig
import com.kursi.engine.PlayerId
import com.kursi.engine.Policy
import com.kursi.feature.game.session.GameSession
import com.kursi.feature.game.session.MatchSnapshot
import com.kursi.feature.game.session.toEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Persistence / resume (M3 §2): the engine is deterministic, so persisting only the seed + the
 * human action log and replaying it reconstructs the EXACT same GameState. These tests prove the
 * round-trip end-to-end, including through the serialized [MatchSnapshot] string.
 */
class MatchResumeTest {
    private val seed = 4242L
    private val players = 4

    private fun makeSession(): GameSession {
        val config = GameConfig.forPlayers(players)
        val bots =
            (1 until players).associate { seat ->
                PlayerId(seat) to EasyPolicy(seed * 31L + seat) as Policy
            }
        return GameSession(config = config, seed = seed, humanSeat = PlayerId(0), bots = bots)
    }

    /**
     * Drive a session forward N human moves with a deterministic policy, return the session plus the
     * GameState at that point. Bot seats auto-advance inside the session between human turns.
     */
    private fun drivePartway(
        session: GameSession,
        humanMoves: Int,
    ): GameSession {
        val humanPolicy = EasyPolicy(99L)
        var ui = session.start()
        var moves = 0
        while (!ui.isGameOver && moves < humanMoves) {
            if (ui.isHumanTurn && ui.legalIntents.isNotEmpty()) {
                ui = session.submitHuman(humanPolicy.decide(ui.view, ui.legalIntents))
                moves++
            } else if (!ui.isHumanTurn) {
                break // session pauses only on human turns; nothing more to drive
            } else {
                break
            }
        }
        return session
    }

    @Test
    fun restore_reproducesIdenticalGameState() {
        // Original run: 6 human moves deep.
        val original = makeSession()
        drivePartway(original, humanMoves = 6)
        val originalState = original.snapshotState()
        val log = original.humanActionLog()
        assertTrue(log.isNotEmpty(), "expected the human to have acted at least once")

        // Fresh session, same seed/config, replay the captured human intent log.
        val resumed = makeSession()
        resumed.restore(log)
        val resumedState = resumed.snapshotState()

        // Deterministic engine ⇒ bit-for-bit identical reconstruction.
        assertEquals(originalState, resumedState, "restore() must reproduce the original GameState")
    }

    @Test
    fun restore_throughSerializedSnapshot_reproducesState() {
        val original = makeSession()
        drivePartway(original, humanMoves = 8)
        val originalState = original.snapshotState()

        // Encode → string → decode, exactly as AppPrefs persistence does.
        val snap =
            MatchSnapshot.of(
                seed = seed,
                players = players,
                difficulty = Difficulty.Medium,
                humanLog = original.humanActionLog(),
            )
        val encoded = snap.encode()
        val decoded =
            MatchSnapshot.decode(encoded)
                ?: error("snapshot failed to decode")
        assertEquals(seed, decoded.seed)
        assertEquals(players, decoded.players)

        val resumed = makeSession()
        resumed.restore(decoded.humanLog.map { it.toEngine() })
        assertEquals(originalState, resumed.snapshotState())
    }

    @Test
    fun emptyLog_restoresToFreshStart() {
        val fresh = makeSession()
        val startState = fresh.start().let { fresh.snapshotState() }

        val resumed = makeSession()
        resumed.restore(emptyList())
        assertEquals(startState, resumed.snapshotState())
    }
}
