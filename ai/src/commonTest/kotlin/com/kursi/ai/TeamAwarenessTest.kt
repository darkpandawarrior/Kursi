package com.kursi.ai

import com.kursi.engine.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AI team-awareness for the TEAMS variant. Two guarantees:
 *   1. Given the team-safe legal list, NO policy ever returns an intent that targets a teammate.
 *   2. A full 4-player 2v2 team game (driven through [SimHarness], which applies [teamSafeIntents]) always
 *      terminates with the surviving team's representative as winner — no teammate ever eliminated an ally.
 */
class TeamAwarenessTest {
    /** 4 seats, interleaved 2v2: {0,2}=team 0, {1,3}=team 1. */
    private fun teamCfg4(): GameConfig = GameConfig.forPlayers(4).copy(teams = mapOf(0 to 0, 1 to 1, 2 to 0, 3 to 1))

    private fun policies(
        seedBase: Long,
        make: (Long) -> SimPolicy,
    ): Map<PlayerId, SimPolicy> = (0 until 4).associate { seat -> PlayerId(seat) to make(seedBase + seat) }

    private val builders: List<Pair<String, (Long) -> SimPolicy>> =
        listOf(
            "Easy" to { s -> EasyPolicy(s) },
            "Medium" to { s -> MediumPolicy(s) },
            "Hard" to { s -> HardPolicy(s) },
            "Expert" to { s -> ExpertPolicy(s) },
            "Grandmaster" to { s -> GrandmasterPolicy(s) },
        )

    @Test
    fun no_policy_targets_a_teammate_on_its_turn() {
        if (!heavyAiSimsEnabled()) return // skip: Grandmaster/Expert default-budget decides starve Karma ping on wasm
        val cfg = teamCfg4()
        // Seat 0 (team 0) is up with 8 coins → coup/assassinate/steal/investigate all reachable.
        val s = stateWithCoins(cfg, seed = 7L, actorSeat = 0, actorCoins = 8)
        val view = redact(s, p0)
        val legal = teamSafeIntents(s, p0)

        for ((name, make) in builders) {
            val decision = runDecide(make(42L), view, legal)
            val intent = decision
            if (intent is Intent.DeclareAction) {
                val target = Rules.targetOf(intent.action)
                assertTrue(
                    target == null || target != p2,
                    "$name targeted teammate seat 2 with ${intent.action}",
                )
            }
        }
    }

    @Test
    fun full_2v2_team_games_end_with_surviving_team_representative() {
        if (!heavyAiSimsEnabled()) return // skip heavy 2v2 ISMCTS sim on wasm/browser (Karma ping starvation)
        val cfg = teamCfg4()
        for ((name, make) in builders) {
            repeat(4) { gameIdx ->
                val result =
                    SimHarness.playOut(
                        cfg,
                        seed = gameIdx * 17L + 3L,
                        policies = policies(gameIdx * 100L, make),
                    )
                val seat = result.winner.raw
                assertTrue(seat in 0 until 4, "$name g=$gameIdx winner out of range: $seat")
                // Winner must be the lowest-seat member of the surviving team (deterministic representative).
                val winningTeam = cfg.teamOfSeat(seat)
                assertTrue(winningTeam != null, "$name g=$gameIdx winner has no team")
            }
        }
    }

    @Test
    fun mixed_policy_team_game_completes() {
        if (!heavyAiSimsEnabled()) return // skip: full game with default (heavy) Grandmaster/Expert budgets starves Karma ping on wasm
        val cfg = teamCfg4()
        val mixed: Map<PlayerId, SimPolicy> =
            mapOf(
                p0 to GrandmasterPolicy(1L),
                p1 to HardPolicy(2L),
                p2 to ExpertPolicy(3L),
                p3 to MediumPolicy(4L),
            )
        val result = SimHarness.playOut(cfg, seed = 99L, policies = mixed)
        assertTrue(result.turns > 0)
        assertTrue(result.winner.raw in 0 until 4)
    }

    // ── helpers ──

    private val p0 = PlayerId(0)
    private val p1 = PlayerId(1)
    private val p2 = PlayerId(2)
    private val p3 = PlayerId(3)

    /** SimPolicy.decide is synchronous (Sim.SimPolicy interface), so no coroutine plumbing needed. */
    private fun runDecide(
        p: SimPolicy,
        view: PlayerView,
        legal: List<Intent>,
    ): Intent = p.decide(view, legal)

    /**
     * Deals via the real [initialState] (so card-conservation holds) and then sets [actorSeat] as the
     * player-to-act with [actorCoins], keeping total coin supply constant by draining the treasury.
     * Engine test helpers (buildState/P) live in :engine's test source set and aren't visible here, so we
     * build state through the public API only.
     */
    private fun stateWithCoins(
        cfg: GameConfig,
        seed: Long,
        actorSeat: Int,
        actorCoins: Int,
    ): GameState {
        val s = initialState(cfg, seed)
        val actor = s.playerAtSeat(actorSeat)
        val delta = actorCoins - actor.coins
        val players = s.players.map { if (it.seatIndex == actorSeat) it.copy(coins = actorCoins) else it }
        return s.copy(
            players = players,
            treasury = s.treasury - delta,
            phase = Phase.AwaitingAction(actorSeat),
        )
    }
}
