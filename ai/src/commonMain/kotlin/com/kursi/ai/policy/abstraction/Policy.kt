package com.kursi.ai.policy.abstraction

/**
 * A generic bot/agent policy: given a redacted [View] of an opaque game state and the concrete legal
 * [Move]s, choose one. Domain-agnostic — knows nothing about Kursi/Coup, [com.kursi.engine], or any
 * other game's types.
 *
 * This is the extraction candidate for the kmp-toolkit `bots-policy` lane (kmp-toolkit-family
 * PROGRESS.md). Kursi's concrete shape is `com.kursi.ai.Policy` (`Policy<PlayerView, Intent>`), which
 * every bot tier in `:ai` (Easy/Medium/Hard/Expert/Grandmaster/Persona) implements.
 */
fun interface Policy<View, Move> {
    fun decide(
        view: View,
        legal: List<Move>,
    ): Move
}
