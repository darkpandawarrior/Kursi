package com.kursi.ai.policy.abstraction

import com.kursi.ai.SearchBudget
import kotlin.math.ln
import kotlin.math.sqrt

/** A move-keyed UCB1 tree node. Exposed so callers can read visit/reward stats after a [Ismcts.search]. */
class SearchNode<Move> internal constructor(
    val move: Move?,
) {
    var visits: Int = 0
    var totalReward: Double = 0.0
    var availability: Int = 0
    val children: MutableMap<Move, SearchNode<Move>> = linkedMapOf()

    fun meanReward(): Double = if (visits == 0) 0.0 else totalReward / visits

    fun ucb(c: Double): Double {
        if (visits == 0 || availability == 0) return Double.MAX_VALUE
        return meanReward() + c * sqrt(ln(availability.toDouble()) / visits)
    }
}

/**
 * Generic Information-Set Monte Carlo Tree Search shell. Knows nothing about Kursi/Coup — every
 * domain concept ([rules], leaf evaluation, rollout policy) is injected by the caller.
 *
 * Determinization (sampling a consistent full [State] from a hidden-information [View]) is
 * deliberately OUT of scope here: [search]'s [determinize] parameter is called once per iteration to
 * supply that fresh determinized root, since HOW hidden information is sampled (belief models,
 * priors, ...) is domain-specific and stays out of this shell (see kmp-toolkit-family PROGRESS.md —
 * BeliefModel/Determinizer/personas are NOT generalized).
 *
 * This is the extraction candidate for the kmp-toolkit `bots-policy` lane. Kursi's wiring lives in
 * `com.kursi.ai.IsmctsSearch`, which constructs this shell per search with `KursiRules`, a per-ply
 * `HardPolicy` factory, and a Coup-specific `staticEval` closure.
 *
 * [rolloutPolicy] is a FACTORY, not a single instance, and is invoked once per rollout ply. This is
 * load-bearing: Kursi's original rollout constructed a fresh `HardPolicy(seed)` per ply, reseeded from
 * a dedicated RNG stream, and the tuned bot-strength floors (Grandmaster/Expert strength-regression
 * suites) depend on that exact per-ply reseeding — a single shared, continuously-advancing rollout
 * policy instance measurably weakens the deeper-searching tiers (Grandmaster 4p fell from ~0.30 to
 * 0.20, below its 0.29 floor). Keeping it a factory reproduces the original seeding while staying
 * domain-agnostic (the factory closes over the caller's own RNG; the shell never sees a seed).
 */
class Ismcts<State, Move, View, Actor>(
    private val rules: GameRules<State, Move, Actor, View>,
    private val rolloutPolicy: () -> Policy<View, Move>,
    /** Leaf evaluation for positions the rollout reaches without terminating (see [rollout]). */
    private val staticEval: (State, Actor) -> Double,
    private val budget: SearchBudget,
) {
    private val ucbExplorationConstant = 0.7

    /**
     * Runs the search and returns the populated root node (one child per element of [legal], plus
     * whatever the tree expanded into). [determinize] supplies one fresh determinized [State] per
     * iteration; a thrown exception from it is treated as a free retry (does not consume an
     * iteration) — the caller is expected to advance its own RNG before rethrowing, mirroring a
     * failed-sample retry. [elapsedMillis] is polled against [SearchBudget.maxMillis].
     */
    fun search(
        determinize: () -> State,
        legal: List<Move>,
        viewer: Actor,
        rolloutHorizon: Int,
        elapsedMillis: () -> Long,
    ): SearchNode<Move> {
        val root = SearchNode<Move>(null)
        for (m in legal) root.children.getOrPut(m) { SearchNode(m) }

        var iterations = 0
        while (iterations < budget.maxIterations && elapsedMillis() < budget.maxMillis) {
            val detState =
                try {
                    determinize()
                } catch (e: Exception) {
                    continue // failed determinization — free retry, doesn't consume an iteration
                }
            try {
                iterate(root, detState, viewer, rolloutHorizon)
            } catch (e: Exception) {
                // ignore — bad determinization mid-tree; continue
            }
            iterations++
        }
        return root
    }

    private fun iterate(
        root: SearchNode<Move>,
        initState: State,
        viewer: Actor,
        rolloutHorizon: Int,
    ) {
        val path = mutableListOf<SearchNode<Move>>()
        var currentNode = root
        var currentState = initState

        // Selection + Expansion
        while (!rules.isTerminal(currentState)) {
            val who = rules.whoActsNext(currentState) ?: break
            val legalNow = rules.legalMoves(currentState, who)
            if (legalNow.isEmpty()) break

            // Update availability for all legal children in this determinization
            for (move in legalNow) {
                currentNode.children.getOrPut(move) { SearchNode(move) }.availability++
            }

            // Find unvisited legal children
            val unvisited =
                legalNow.filter {
                    val child = currentNode.children[it]
                    child == null || child.visits == 0
                }

            val chosenMove =
                if (unvisited.isNotEmpty()) {
                    // Expand: pick one unvisited (first for determinism)
                    unvisited.first()
                } else {
                    // Select via UCB1
                    legalNow.maxByOrNull { move ->
                        currentNode.children[move]?.ucb(ucbExplorationConstant) ?: Double.MAX_VALUE
                    } ?: legalNow.first()
                }

            path.add(currentNode)
            val childNode = currentNode.children.getOrPut(chosenMove) { SearchNode(chosenMove) }

            currentState =
                when (val outcome = rules.apply(currentState, chosenMove)) {
                    is Outcome.Accepted -> outcome.state
                    is Outcome.Rejected -> break // this determinization is invalid at this point
                }
            currentNode = childNode
            // After visiting a new node, go to rollout
            if (currentNode.visits == 0) break
        }
        path.add(currentNode)

        // Rollout
        val reward = rollout(currentState, viewer, rolloutHorizon)

        // Backpropagate
        for (node in path) {
            node.visits++
            node.totalReward += reward
        }
    }

    private fun rollout(
        state: State,
        viewer: Actor,
        rolloutHorizon: Int,
    ): Double {
        var currentState = state
        var plies = 0
        while (plies < rolloutHorizon && !rules.isTerminal(currentState)) {
            val who = rules.whoActsNext(currentState) ?: break
            val legalNow = rules.legalMoves(currentState, who)
            if (legalNow.isEmpty()) break
            val view = rules.redact(currentState, who)
            val move =
                try {
                    rolloutPolicy().decide(view, legalNow)
                } catch (e: Exception) {
                    legalNow.first()
                }
            currentState =
                when (val outcome = rules.apply(currentState, move)) {
                    is Outcome.Accepted -> outcome.state
                    is Outcome.Rejected -> break
                }
            plies++
        }
        if (rules.isTerminal(currentState)) {
            return if (rules.winner(currentState) == viewer) 1.0 else 0.0
        }
        return staticEval(currentState, viewer)
    }
}
