package com.kursi.feature.game

import com.kursi.ai.EasyPolicy
import com.kursi.engine.GameConfig
import com.kursi.engine.Phase
import com.kursi.engine.PlayerId
import com.kursi.engine.Policy
import com.kursi.feature.game.session.GameSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M6e TEAM KHEL — verifies the seat→team partition helper and that the session, driven through to
 * game-over on a real team [GameConfig], ends on a last-team-standing win where the winner's whole
 * team is wiped from the opposition (i.e. teammate-hostile bot moves are filtered by teamSafeIntents).
 */
class TeamKhelTest {
    @Test
    fun build_alternatesSeatsIntoTeams() {
        val map = TeamAssignment.build(playerCount = 4, teamCount = 2)
        assertNotNull(map)
        assertEquals(mapOf(0 to 0, 1 to 1, 2 to 0, 3 to 1), map)
        // Every seat is mapped, and there are >= 2 distinct teams.
        assertEquals((0 until 4).toSet(), map.keys)
        assertTrue(map.values.toSet().size >= 2)
    }

    @Test
    fun build_returnsNull_forFreeForAllOrImpossibleSplit() {
        assertNull(TeamAssignment.build(playerCount = 4, teamCount = 1)) // free-for-all
        assertNull(TeamAssignment.build(playerCount = 1, teamCount = 2)) // can't fill 2 teams
    }

    @Test
    fun teamOfSeat_isAlternating() {
        assertEquals(0, TeamAssignment.teamOfSeat(0))
        assertEquals(1, TeamAssignment.teamOfSeat(1))
        assertEquals(0, TeamAssignment.teamOfSeat(2))
        assertEquals(1, TeamAssignment.teamOfSeat(3))
    }

    @Test
    fun teamGame_endsOnLastTeamStanding() {
        // A full 4-seat 2v2 game where every seat (incl. "human") is driven by a bot policy. The
        // session applies teamSafeIntents to bot choices, so allies never coup/assassinate/steal each
        // other. The game must still terminate, and the surviving team is the winner's team.
        val teams = TeamAssignment.build(playerCount = 4, teamCount = 2)!!
        val config = GameConfig.forPlayers(4).copy(teams = teams)
        val seed = 12345L
        // Seat 0 is the "human" seat but we feed it a bot policy too, so the whole table is bot-driven.
        val bots: Map<PlayerId, Policy> = (1 until 4).associate { PlayerId(it) to EasyPolicy(seed + it) as Policy }
        val session = GameSession(config = config, seed = seed, humanSeat = PlayerId(0), bots = bots)

        val human = EasyPolicy(seed)
        var ui = session.start()
        var guard = 0
        while (!ui.isGameOver && ui.isHumanTurn && ui.legalIntents.isNotEmpty() && guard < 5000) {
            ui = session.submitHuman(human.decide(ui.view, ui.legalIntents))
            guard++
        }
        assertTrue(ui.isGameOver, "team game should terminate")
        val finalState = session.snapshotState()
        val over = finalState.phase as Phase.GameOver
        // Exactly one team should remain alive at game-over (last-team-standing).
        assertEquals(1, finalState.aliveTeams().size, "exactly one team alive at the end")
        // The reported winner belongs to the surviving team.
        val winnerTeam = finalState.config.teamOfSeat(finalState.seatOf(over.winner))
        assertEquals(finalState.aliveTeams().first(), winnerTeam)
    }
}
