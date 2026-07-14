package com.kursi.ai.policy.abstraction

/** Generic outcome of applying a [Move] to a [State] — mirrors engine's `ApplyOutcome`, domain-agnostic. */
sealed interface Outcome<out State> {
    data class Accepted<out State>(
        val state: State,
    ) : Outcome<State>

    data class Rejected(
        val reason: String,
    ) : Outcome<Nothing>
}

/**
 * The generic rules contract a game engine exposes so a domain-agnostic search (see [Ismcts]) can
 * drive self-play without knowing the concrete rules. [View] is the redacted/secrecy-safe projection
 * of [State] a [Policy] is allowed to see (produced by [redact]) — kept as its own type parameter
 * (rather than derived from [State]) because the secrecy boundary is a distinct type in every engine
 * that would adopt this contract (Kursi's [State]=`GameState` vs [View]=`PlayerView`).
 *
 * Domain-agnostic — this is the extraction candidate for the kmp-toolkit `bots-policy` lane. Kursi's
 * concrete wiring is `com.kursi.ai.KursiRules` (`GameRules<GameState, Intent, PlayerId, PlayerView>`).
 */
interface GameRules<State, Move, Actor, View> {
    /** The single actor whose input is needed next, or null when [state] is terminal. */
    fun whoActsNext(state: State): Actor?

    /** Concrete legal moves for [actor] given [state] (only non-empty when it is [actor]'s turn to act). */
    fun legalMoves(
        state: State,
        actor: Actor,
    ): List<Move>

    /** Validate [move] against [state] and produce the next state, or reject it. */
    fun apply(
        state: State,
        move: Move,
    ): Outcome<State>

    /** True once [state] is decided — no further moves are legal. */
    fun isTerminal(state: State): Boolean

    /** The winning [Actor], or null if [state] is not yet terminal. */
    fun winner(state: State): Actor?

    /** Project [state] to what [viewer] is allowed to see — the strict-subset secrecy boundary. */
    fun redact(
        state: State,
        viewer: Actor,
    ): View
}
