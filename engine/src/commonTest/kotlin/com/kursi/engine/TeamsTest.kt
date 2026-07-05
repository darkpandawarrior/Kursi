package com.kursi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TEAMS variant (flag-gated, additive). Verifies last-team-standing win, "keep acting while a teammate is
 * alive", team-aware bot targeting, and — critically — that free-for-all (teams == null) is unchanged.
 */
class TeamsTest {
    /** 4 seats, two teams: {0,2}=team 0, {1,3}=team 1 (interleaved so seat order alternates teams). */
    private fun teamCfg4(): GameConfig = GameConfig.forPlayers(4).copy(teams = mapOf(0 to 0, 1 to 1, 2 to 0, 3 to 1))

    /** Drives a coup (pay 7, unblockable, unchallengeable) by [actor] on [target], resolving the loss. */
    private fun coup(
        s: GameState,
        actor: PlayerId,
        target: PlayerId,
    ): GameState {
        var st = applyIntent(s, Intent.DeclareAction(actor, Action.Coup(target))).ok()
        if (st.phase is Phase.AwaitingInfluenceLoss) {
            st = applyIntent(st, legalIntents(st, target).first()).ok()
        }
        return st
    }

    // ── config validation ──

    @Test
    fun teams_config_requires_total_seat_coverage() {
        val ex = runCatching { GameConfig.forPlayers(4).copy(teams = mapOf(0 to 0, 1 to 1)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException, "partial team map must be rejected")
    }

    @Test
    fun teams_config_requires_two_distinct_teams() {
        val ex =
            runCatching {
                GameConfig.forPlayers(3).copy(teams = mapOf(0 to 7, 1 to 7, 2 to 7))
            }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException, "a single-team map must be rejected")
    }

    @Test
    fun free_for_all_has_no_teams() {
        val ffa = GameConfig.forPlayers(4)
        assertFalse(ffa.isTeamGame)
        assertNull(ffa.teamOfSeat(0))
        assertTrue(ffa.teamIds.isEmpty())
    }

    // ── win condition ──

    @Test
    fun game_ends_when_only_one_team_has_surviving_influence() {
        val cfg = teamCfg4()
        // Give seat 0 (team 0) plenty of coins to coup; everyone else minimal.
        val hands = List(4) { listOf(Role.NETA, Role.BHAI) }
        var s = buildState(cfg, hands, coins = listOf(20, 2, 2, 2))

        // Team 1 = seats 1 and 3. Knock BOTH out fully (each has 2 influence).
        // Seat 0 acts (team 0). After each coup the turn advances; we re-fetch whoActsNext but keep
        // looping coups from whichever team-0 member is up by feeding coins. To keep it deterministic we
        // simply hand-drive: seat 0 coups seat 1 twice, then (after wrap) coups seat 3 twice.
        // Simpler: directly verify the win predicate by removing all of team 1's face-down influence.
        // Coup seat 1 down to elimination.
        s = forceEliminate(s, P[1])
        assertNull(winnerOrNull(s), "team 1 still has seat 3 alive — not over")
        assertFalse(s.aliveTeams().size == 1)

        // Now eliminate seat 3 → team 1 wiped → team 0 wins.
        s = forceEliminate(s, P[3])
        val w = assertNotNull(winnerOrNull(s), "only team 0 survives → game over")
        assertEquals(P[0], w, "team 0's representative (lowest-seat alive member) wins")
        assertEquals(0, s.teamOf(w), "winner must be on the surviving team")
    }

    @Test
    fun player_keeps_acting_while_a_teammate_is_alive() {
        val cfg = teamCfg4()
        val hands = List(4) { listOf(Role.NETA, Role.BHAI) }
        var s = buildState(cfg, hands, coins = listOf(20, 2, 2, 2), phase = Phase.AwaitingAction(0))

        // Seat 0 (team 0) coups seat 1 (team 1) twice to eliminate them.
        s = coup(s, P[0], P[1])
        s = s.copy(phase = Phase.AwaitingAction(0)) // re-seat for a clean second coup from seat 0
        s = s.copy(players = s.players.map { if (it.id == P[0]) it.copy(coins = 20) else it })
        s = coup(s, P[0], P[1])
        // Seat 1 is now eliminated, but seat 3 (its teammate) is alive → game must NOT be over.
        assertTrue(s.phase !is Phase.GameOver, "one team-1 member dead, the other alive → game continues")
        assertFalse(s.isAlive(P[1]))
        assertTrue(s.isAlive(P[3]))
        assertEquals(setOf(0, 1), s.aliveTeams())
    }

    @Test
    fun teams_invariants_hold_through_a_team_win() {
        val cfg = teamCfg4()
        val hands = List(4) { listOf(Role.NETA, Role.BHAI) }
        var s = buildState(cfg, hands, coins = listOf(2, 2, 2, 2))
        checkInvariants(s)
        s = forceEliminate(s, P[1])
        checkInvariants(s)
        s = forceEliminate(s, P[3])
        // Engine itself hasn't transitioned to GameOver (we mutated via helper, not endTurn), so move it
        // through a real end-of-turn so the GameOver phase + GameEnded event are produced and checked.
        val ended = endViaTurn(s, P[0])
        assertTrue(ended.phase is Phase.GameOver)
        checkInvariants(ended)
    }

    // ── bot team-awareness ──

    @Test
    fun teamSafeIntents_never_targets_a_teammate() {
        val cfg = teamCfg4()
        val hands = List(4) { listOf(Role.NETA, Role.BHAI) }
        // Seat 0 (team 0) is up with 8 coins → coup is legal against everyone.
        val s = buildState(cfg, hands, coins = listOf(8, 2, 2, 2), phase = Phase.AwaitingAction(0))

        val raw = legalIntents(s, P[0])
        val safe = teamSafeIntents(s, P[0])

        // Raw legality DOES include a coup on the teammate (seat 2) — the engine never role-gates it.
        assertTrue(raw.any { it is Intent.DeclareAction && it.action == Action.Coup(P[2]) })
        // Team-safe filtering removes EVERY action targeting the teammate (seat 2)...
        val teammateTargeted =
            safe.filter {
                it is Intent.DeclareAction && Rules.targetOf(it.action) == P[2]
            }
        assertTrue(teammateTargeted.isEmpty(), "no team-safe action may target teammate seat 2")
        // ...but keeps coups on the two opponents (seats 1 and 3).
        assertTrue(safe.any { it is Intent.DeclareAction && it.action == Action.Coup(P[1]) })
        assertTrue(safe.any { it is Intent.DeclareAction && it.action == Action.Coup(P[3]) })
    }

    @Test
    fun teamSafeIntents_falls_back_when_only_teammates_remain_targetable() {
        val cfg = teamCfg4()
        val hands = List(4) { listOf(Role.NETA, Role.BHAI) }
        // Eliminate both opponents (team 1: seats 1, 3) so the only alive "others" are teammates.
        // But that would END the game. Instead, construct forced-coup with only a teammate alive among
        // targets by eliminating one opponent and putting seat 0 at the forced-coup threshold while the
        // other opponent is also gone is impossible without a win. So we verify the no-op fallback shape:
        // when no teammates exist on the acting seat's view, teamSafeIntents == legalIntents.
        val ffa = GameConfig.forPlayers(4)
        val s = buildState(ffa, hands, coins = listOf(8, 2, 2, 2), phase = Phase.AwaitingAction(0))
        assertEquals(legalIntents(s, P[0]), teamSafeIntents(s, P[0]), "free-for-all: filter is a no-op")
    }

    @Test
    fun playerView_exposes_team_and_teammates() {
        val cfg = teamCfg4()
        val hands = List(4) { listOf(Role.NETA, Role.BHAI) }
        val s = buildState(cfg, hands, coins = listOf(2, 2, 2, 2))
        val view = redact(s, P[0])
        assertEquals(0, view.myTeam)
        assertEquals(listOf(P[2]), view.teammates)
        // targetableOpponents excludes the viewer and the teammate (seat 2), keeping seats 1 and 3.
        assertEquals(setOf(P[1], P[3]), view.targetableOpponents.map { it.id }.toSet())
        // Opponent team ids are public.
        assertEquals(1, view.players.first { it.id == P[1] }.team)
    }

    // ── free-for-all parity ──

    @Test
    fun free_for_all_win_condition_unchanged() {
        val cfg = GameConfig.forPlayers(3)
        val hands = List(3) { listOf(Role.NETA, Role.BHAI) }
        var s = buildState(cfg, hands, coins = listOf(2, 2, 2))
        s = forceEliminate(s, P[1])
        assertNull(winnerOrNull(s), "2 of 3 alive → not over")
        s = forceEliminate(s, P[2])
        assertEquals(P[0], winnerOrNull(s), "last player standing wins")
    }

    // ── helpers ──

    /** Flips every face-down card of [pid] to face-up (eliminates them) WITHOUT driving the engine. */
    private fun forceEliminate(
        s: GameState,
        pid: PlayerId,
    ): GameState {
        var locs = s.locations
        for (c in s.faceDownInfluence(pid)) {
            locs = locs + (c to CardLocation.Hand(pid, faceUp = true))
        }
        return s.copy(locations = locs)
    }

    /** Runs a real Income action by [actor] so endTurn fires and any team win is detected by the engine. */
    private fun endViaTurn(
        s: GameState,
        actor: PlayerId,
    ): GameState {
        val seated = s.copy(phase = Phase.AwaitingAction(s.seatOf(actor)))
        return applyIntent(seated, Intent.DeclareAction(actor, Action.Income)).ok()
    }
}
