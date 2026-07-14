package com.kursi.feature.game.narrative

import com.kursi.ai.EasyPolicy
import com.kursi.ai.Policy
import com.kursi.ai.persona.PersonalityProfile
import com.kursi.ai.persona.TargetingBias
import com.kursi.engine.GameConfig
import com.kursi.engine.PlayerId
import com.kursi.feature.game.session.GameSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DETERMINISM: narrative-mode resume must reconstruct a bit-for-bit identical [GameState].
 *
 * We drive a [GameSession] in narrative mode (SocialDirector attached), submit a handful of human
 * moves plus a couple of chat inputs, capture the [GameState], then replay from a FRESH session
 * using the same human log + chat log and assert the restored [GameState] equals the original.
 *
 * This mirrors [com.kursi.feature.game.MatchResumeTest] exactly — the only additions are the
 * [SocialDirector] wiring and the chat log round-trip.
 *
 * SCOPE NOTE: the test keeps the move count very small (≤ 5 human moves, ≤ 2 chat inputs)
 * and uses deterministic [EasyPolicy] bots so the session completes quickly on CI.  The
 * correctness guarantee comes from the engine determinism contract (same seed + same intent
 * sequence ⇒ same GameState), not from the chat feed content.
 */
class NarrativeResumeTest {
    private val seed = 7777L
    private val players = 4

    // ── Minimal SeatInfo fixtures ─────────────────────────────────────────────

    /**
     * Build a [SeatInfo] list for the test session: seat 0 is the human; seats 1–3 are bots
     * with lightweight inline [PersonalityProfile]s (not the full roster personas).
     */
    private fun buildSeatInfos(): List<SeatInfo> {
        val botProfile =
            PersonalityProfile(
                bluffRate = 0.30f,
                challengeAggression = 0.40f,
                economicAggression = 0.50f,
                targetingBias = TargetingBias.LEADER,
                risk = 0.50f,
                vindictiveness = 0.40f,
                predictability = 0.60f,
            )
        return listOf(
            SeatInfo(0, "Aap", personaId = null, profile = null, isHuman = true),
            SeatInfo(1, "Bhai Teja", personaId = "bhai_teja", profile = botProfile, isHuman = false),
            SeatInfo(2, "Babu", personaId = "babu_filewala", profile = botProfile.copy(economicAggression = 0.20f), isHuman = false),
            SeatInfo(3, "Jugaadu", personaId = "jugaadu_chhotu", profile = botProfile.copy(predictability = 0.20f), isHuman = false),
        )
    }

    private fun makeDirector() =
        SocialDirector(
            seed = seed,
            seats = buildSeatInfos(),
            humanSeat = 0,
        )

    private fun makeBots(): Map<PlayerId, Policy> =
        (1 until players).associate { seat ->
            PlayerId(seat) to EasyPolicy(seed * 31L + seat) as Policy
        }

    /** Builds a narrative-mode [GameSession] backed by [EasyPolicy] bots. */
    private fun makeSession(): GameSession {
        val config = GameConfig.forPlayers(players)
        return GameSession(
            config = config,
            seed = seed,
            humanSeat = PlayerId(0),
            bots = makeBots(),
            socialDirector = makeDirector(),
        )
    }

    // ── Drive helpers ─────────────────────────────────────────────────────────

    /**
     * Drive [session] forward by up to [humanMoves] human-submitted moves using a deterministic
     * [EasyPolicy]. Returns when the human has acted [humanMoves] times or the game ends.
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
            } else {
                break
            }
        }
        return session
    }

    /**
     * Inject chat inputs into [session] after specific human-move boundaries, mirroring
     * what a player would do in the Darbar.
     */
    private fun injectSomeChatInputs(session: GameSession) {
        // After move 0 (before the player's first move), start an AFWAAH arc against seat 1.
        // The suggestion id and targetSeat are free-form; the session / director use them
        // to evolve the social fabric and log the entry for replay.
        val afwaahStart =
            HumanChatInput(
                suggestionId = "start.afwaah.1",
                kind = ChatActionKind.ARC_START,
                arc = ArcId.AFWAAH,
                targetSeat = 1,
            )
        session.applyHumanChat(afwaahStart)

        // After the chat entry is logged, also send a TAUNT targeting seat 2.
        val taunt =
            HumanChatInput(
                suggestionId = "talk.taunt.2",
                kind = ChatActionKind.TAUNT,
                arc = null,
                targetSeat = 2,
            )
        session.applyHumanChat(taunt)
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun narrativeResume_reproducesIdenticalGameState() {
        // ── Original run ──────────────────────────────────────────────────────
        val original = makeSession()
        var ui = original.start()

        // Inject chat before any human moves (the log records afterHumanMoves=0 for both).
        injectSomeChatInputs(original)

        // Drive 4 human moves forward.
        val humanPolicy = EasyPolicy(99L)
        var humanMoveCount = 0
        while (!ui.isGameOver && humanMoveCount < 4) {
            if (ui.isHumanTurn && ui.legalIntents.isNotEmpty()) {
                ui = original.submitHuman(humanPolicy.decide(ui.view, ui.legalIntents))
                humanMoveCount++
            } else {
                break
            }
        }

        val originalState = original.snapshotState()
        val humanLog = original.humanActionLog()
        val chatLog = original.chatLog()

        assertTrue(
            chatLog.isNotEmpty(),
            "chat log must be non-empty after two injected chat inputs",
        )
        // We may have made zero human moves if the game ended instantly on bots, but typically > 0.
        // (seed 7777 / 4 players always lets the human act first so this should be > 0.)

        // ── Fresh session, replay ──────────────────────────────────────────────
        val resumed = makeSession()
        resumed.restore(humanLog, chatLog)
        val resumedState = resumed.snapshotState()

        // Bit-for-bit identical — this is the contract.
        assertEquals(
            originalState,
            resumedState,
            "narrative restore() must reproduce the original GameState bit-for-bit",
        )
    }

    @Test
    fun narrativeResume_emptyLogs_restoresToFreshStart() {
        val fresh = makeSession()
        val startState = fresh.start().let { fresh.snapshotState() }

        val resumed = makeSession()
        resumed.restore(emptyList(), emptyList())
        assertEquals(
            startState,
            resumed.snapshotState(),
            "restoring with empty logs must yield the same state as a plain start()",
        )
    }

    @Test
    fun chatLog_isTaggedWithCorrectHumanMoveIndex() {
        // Send a chat before any human move → afterHumanMoves must be 0.
        // Then submit one human move, send another chat → afterHumanMoves must be 1.
        val session = makeSession()
        var ui = session.start()

        val chat0 =
            HumanChatInput(
                suggestionId = "start.afwaah.1",
                kind = ChatActionKind.ARC_START,
                arc = ArcId.AFWAAH,
                targetSeat = 1,
            )
        session.applyHumanChat(chat0)

        // Drive one human move.
        val humanPolicy = EasyPolicy(99L)
        if (!ui.isGameOver && ui.isHumanTurn && ui.legalIntents.isNotEmpty()) {
            ui = session.submitHuman(humanPolicy.decide(ui.view, ui.legalIntents))
        }

        val chat1 =
            HumanChatInput(
                suggestionId = "talk.taunt.2",
                kind = ChatActionKind.TAUNT,
                arc = null,
                targetSeat = 2,
            )
        session.applyHumanChat(chat1)

        val log = session.chatLog()
        assertTrue(log.isNotEmpty(), "chat log must be non-empty")
        val first = log.first()
        assertEquals(
            0,
            first.first,
            "first chat (sent before any human move) must be tagged afterHumanMoves=0",
        )
    }

    @Test
    fun narrativeResume_chatFeedIsNonEmpty_afterArcs() {
        // After starting an AFWAAH arc the feed should contain at least the two beats.
        val session = makeSession()
        session.start()

        session.applyHumanChat(
            HumanChatInput(
                suggestionId = "start.afwaah.1",
                kind = ChatActionKind.ARC_START,
                arc = ArcId.AFWAAH,
                targetSeat = 1,
            ),
        )
        val state = session.currentUiState()
        assertTrue(
            state.chatFeed.isNotEmpty(),
            "chat feed must be non-empty after an AFWAAH arc start",
        )
        // At least the player beat + narrator beat from AFWAAH.begin.
        assertTrue(
            state.chatFeed.size >= 2,
            "AFWAAH.begin emits at least 2 beats (player + narrator); got ${state.chatFeed.size}",
        )
    }
}
