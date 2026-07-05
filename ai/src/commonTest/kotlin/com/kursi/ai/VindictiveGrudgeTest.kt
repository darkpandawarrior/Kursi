package com.kursi.ai

import com.kursi.ai.persona.BarkSet
import com.kursi.ai.persona.BotPersona
import com.kursi.ai.persona.PersonaPolicy
import com.kursi.ai.persona.PersonalityProfile
import com.kursi.ai.persona.TargetingBias
import com.kursi.engine.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * M2 grudge wiring — proves a VINDICTIVE persona measurably retaliates against whoever hit it
 * recently, and that the grudge map decays over time.
 *
 * The production grudge plumbing lives in GameSession ([feature:game]); these tests reproduce the
 * SAME causal attribution here (over the public event stream) so the behaviour is provable inside
 * the [:ai] gate without depending on the feature module.
 */
class VindictiveGrudgeTest {
    /** Build a VINDICTIVE persona over a Hard base, with the given vindictiveness. */
    private fun vindictivePersona(
        seed: Long,
        vindictiveness: Float = 0.9f,
    ): PersonaPolicy {
        val profile =
            PersonalityProfile(
                bluffRate = 0.40f,
                challengeAggression = 0.50f,
                economicAggression = 0.70f,
                targetingBias = TargetingBias.VINDICTIVE,
                risk = 0.50f,
                vindictiveness = vindictiveness,
                predictability = 0.50f,
            )
        val persona =
            BotPersona(
                id = "test_vindictive",
                name = "Test Vindictive",
                title = "",
                archetype = "",
                seatColorArgb = 0xFF000000L,
                monogram = "TV",
                personality = profile,
                barks = BarkSet(emptyMap()),
            )
        return PersonaPolicy(persona, HardPolicy(seed), seed)
    }

    // ── Unit: notifyHit steers targeting toward the grudge holder ───────────────

    @Test
    fun vindictive_retargets_toward_recent_attacker() {
        // Base policy that ALWAYS declares Coup on the FIRST legal Coup target (a fixed scapegoat),
        // so any retarget we observe is purely the persona's grudge logic at work.
        val scapegoatBase =
            Policy { _, legal ->
                legal
                    .filterIsInstance<Intent.DeclareAction>()
                    .firstOrNull { it.action is Action.Coup }
                    ?: legal.first()
            }
        val profile =
            PersonalityProfile(
                bluffRate = 0.0f,
                challengeAggression = 0.0f,
                economicAggression = 1.0f,
                targetingBias = TargetingBias.VINDICTIVE,
                risk = 0.5f,
                vindictiveness = 1.0f,
                predictability = 1.0f,
            )
        val persona =
            BotPersona(
                id = "v",
                name = "V",
                title = "",
                archetype = "",
                seatColorArgb = 0L,
                monogram = "V",
                personality = profile,
                barks = BarkSet(emptyMap()),
            )
        val policy = PersonaPolicy(persona, scapegoatBase, seed = 1L)

        // Viewer = seat 0; opponents are seats 1, 2, 3. The base would Coup the lowest-id target;
        // we hold a grudge against seat 3, so the persona must retarget the Coup onto seat 3.
        val viewer = PlayerId(0)
        val view = turnViewWithCoupTargets(viewer, opponents = listOf(1, 2, 3))
        val legal =
            listOf(
                Intent.DeclareAction(viewer, Action.Coup(PlayerId(1))),
                Intent.DeclareAction(viewer, Action.Coup(PlayerId(2))),
                Intent.DeclareAction(viewer, Action.Coup(PlayerId(3))),
            )

        // No grudge yet → base scapegoat (seat 1) survives.
        val before = policy.decide(view, legal) as Intent.DeclareAction
        assertTrue(
            (before.action as Action.Coup).target == PlayerId(1),
            "with no grudge, persona should keep the base's scapegoat target (seat 1) but got ${before.action}",
        )

        // Seat 3 hits us → grudge builds. Now the persona must retaliate onto seat 3.
        policy.notifyHit(PlayerId(3), weight = 2)
        val after = policy.decide(view, legal) as Intent.DeclareAction
        assertTrue(
            (after.action as Action.Coup).target == PlayerId(3),
            "after seat 3 hit us, VINDICTIVE persona should Coup seat 3 but targeted ${after.action}",
        )
        assertTrue(policy.grudgeAgainst(PlayerId(3)) > 0.0, "grudge against seat 3 should be positive")
    }

    @Test
    fun grudge_decays_toward_zero_over_turns() {
        val policy = vindictivePersona(seed = 7L, vindictiveness = 1.0f)
        policy.notifyHit(PlayerId(2), weight = 4)
        val initial = policy.grudgeAgainst(PlayerId(2))
        assertTrue(initial > 0.0, "grudge should start positive")

        repeat(3) { policy.notifyTurnPassed() }
        val decayed = policy.grudgeAgainst(PlayerId(2))
        assertTrue(decayed < initial, "grudge should shrink after turns pass ($decayed should be < $initial)")

        repeat(30) { policy.notifyTurnPassed() }
        assertTrue(
            policy.grudgeAgainst(PlayerId(2)) == 0.0,
            "after many turns the grudge should fully fade, was ${policy.grudgeAgainst(PlayerId(2))}",
        )
    }

    // ── Integration: VINDICTIVE seat attacks its recent attacker above baseline ──

    @Test
    fun vindictive_attacks_recent_attacker_above_baseline() {
        // Two identical-base 4-player sims. In BOTH, seat 0 is a VINDICTIVE persona; in the GRUDGE
        // run we feed it notifyHit/decay from the event stream, in the CONTROL run we do not. We
        // measure how often seat 0's targeted attacks land on whoever most recently hit it.
        val config = GameConfig.forPlayers(4)
        var grudgeHits = 0
        var grudgeAttacks = 0
        var controlHits = 0
        var controlAttacks = 0

        for (gameIdx in 0 until 60) {
            val seed = gameIdx.toLong() * 101L + 7L
            grudgePlayout(config, seed, wireGrudge = true).let {
                grudgeHits += it.first
                grudgeAttacks += it.second
            }
            grudgePlayout(config, seed, wireGrudge = false).let {
                controlHits += it.first
                controlAttacks += it.second
            }
        }

        // Both runs must produce attacks (otherwise the metric is vacuous).
        assertTrue(grudgeAttacks > 0 && controlAttacks > 0, "expected attacks in both runs")

        val grudgeRate = grudgeHits.toDouble() / grudgeAttacks
        val controlRate = controlHits.toDouble() / controlAttacks
        assertTrue(
            grudgeRate > controlRate,
            "VINDICTIVE retaliation rate ($grudgeRate) should exceed the no-grudge baseline ($controlRate). " +
                "grudge=$grudgeHits/$grudgeAttacks control=$controlHits/$controlAttacks",
        )
    }

    /**
     * Play one game where seat 0 is VINDICTIVE. Returns (attacksOnLastAttacker, totalTargetedAttacks)
     * for seat 0. When [wireGrudge] is true, seat 0 receives notifyHit/decay attributed from the
     * public event stream (the same logic GameSession uses).
     */
    private fun grudgePlayout(
        config: GameConfig,
        seed: Long,
        wireGrudge: Boolean,
    ): Pair<Int, Int> {
        val vindictive = vindictivePersona(seed = seed, vindictiveness = 1.0f)
        val policies: Map<PlayerId, Policy> =
            mapOf(
                PlayerId(0) to vindictive,
                PlayerId(1) to HardPolicy(seed + 11L),
                PlayerId(2) to HardPolicy(seed + 22L),
                PlayerId(3) to HardPolicy(seed + 33L),
            )

        var state = initialState(config, seed)
        var lastActionActor: PlayerId? = null
        var lastChallenger: PlayerId? = null
        // The most-recent seat that hit seat 0 (our retaliation target of record).
        var lastAttackerOfSeat0: PlayerId? = null
        var attacks = 0
        var hits = 0
        var steps = 0

        while (state.phase !is Phase.GameOver && steps++ < 200_000) {
            val who = whoActsNext(state) ?: break
            val legal = legalIntents(state, who)
            if (legal.isEmpty()) break
            val intent = policies.getValue(who).decide(redact(state, who), legal)

            // Measure: when seat 0 declares a TARGETED attack, is it aimed at its last attacker?
            if (who == PlayerId(0) && intent is Intent.DeclareAction) {
                Rules.targetOf(intent.action)?.let { tgt ->
                    attacks++
                    if (lastAttackerOfSeat0 != null && tgt == lastAttackerOfSeat0) hits++
                }
            }

            val outcome = applyIntent(state, intent)
            if (outcome !is ApplyOutcome.Accepted) error("illegal: $intent")
            state = outcome.state

            for (ev in outcome.events) {
                // Reconstruct aggressor attribution (mirrors GameSession.routeGrudges).
                when (ev) {
                    is GameEvent.ActionDeclared -> lastActionActor = ev.actor
                    is GameEvent.Challenged -> lastChallenger = ev.challenger
                    is GameEvent.InfluenceLost -> {
                        val aggressor =
                            when (ev.reason) {
                                LossReason.ASSASSINATED, LossReason.COUPED, LossReason.EMERGENCY_COUPED -> lastActionActor
                                LossReason.LOST_CHALLENGE, LossReason.LOST_BLOCK_CHALLENGE -> lastChallenger
                                LossReason.SABOTAGED -> null // voluntary; no grudge target
                            }
                        if (ev.player == PlayerId(0) && aggressor != null && aggressor != PlayerId(0)) {
                            lastAttackerOfSeat0 = aggressor
                            if (wireGrudge) vindictive.notifyHit(aggressor, weight = 2)
                        }
                    }
                    is GameEvent.CoinsTransferred -> {
                        if (ev.from == PlayerId(0) && ev.to != PlayerId(0)) {
                            lastAttackerOfSeat0 = ev.to
                            if (wireGrudge) vindictive.notifyHit(ev.to, weight = 1)
                        }
                    }
                    is GameEvent.TurnAdvanced -> {
                        if (wireGrudge) vindictive.notifyTurnPassed()
                        lastActionActor = null
                        lastChallenger = null
                    }
                    else -> {}
                }
            }
        }
        return hits to attacks
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** A minimal Turn-phase [PlayerView] for [viewer] with [opponents] alive, each holding 2 cards. */
    private fun turnViewWithCoupTargets(
        viewer: PlayerId,
        opponents: List<Int>,
    ): PlayerView {
        val config = GameConfig.forPlayers(opponents.size + 1)
        val oppViews =
            (listOf(viewer.raw) + opponents).map { raw ->
                OpponentView(
                    id = PlayerId(raw),
                    seatIndex = raw,
                    coins = if (raw == viewer.raw) 7 else 2,
                    faceUpRoles = emptyList(),
                    faceDownCount = 2,
                    eliminated = false,
                )
            }
        return PlayerView(
            viewer = viewer,
            config = config,
            treasury = 40,
            deckCount = 5,
            turnNumber = 1,
            myCoins = 7,
            myInfluence = listOf(Role.NETA, Role.BHAI),
            myFaceUp = emptyList(),
            myCards =
                listOf(
                    OwnCard(CardId(0), Role.NETA, faceUp = false),
                    OwnCard(CardId(1), Role.BHAI, faceUp = false),
                ),
            players = oppViews,
            phase = PhaseView.Turn(actor = viewer),
        )
    }
}
