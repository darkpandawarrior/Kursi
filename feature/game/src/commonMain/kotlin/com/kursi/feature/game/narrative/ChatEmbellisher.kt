package com.kursi.feature.game.narrative

import com.kursi.feature.game.Language

/**
 * Pluggable seam for OPTIONAL live-LLM chat flavour.
 *
 * The deterministic template engine ([ChatVoice]) is ALWAYS the spine — it ships identically offline
 * on every target (android / ios / desktop / wasm), costs nothing, and is exactly what deterministic
 * resume replays. An ONLINE, opted-in match MAY install a real LLM embellisher to rewrite a
 * template line into richer, improvised prose in the same persona's voice.
 *
 * HARD CONTRACT: an embellisher NEVER changes a game decision or the social state — it only restyles
 * a line the template engine already produced. So determinism, resume and the engine's redaction
 * boundary are all untouched. A null return (timeout / failure / offline) MUST fall back to the
 * template line. This is why the core loop never awaits it on the deterministic path.
 */
interface ChatEmbellisher {
    suspend fun embellish(request: EmbellishRequest): String?
}

/** Everything an embellisher needs to restyle one line — all PUBLIC, table-visible context. */
data class EmbellishRequest(
    val personaId: String,
    val personaName: String,
    val personaTitle: String,
    val archetype: String,
    val baseLine: String,
    val tone: MessageTone,
    val arc: ArcId?,
    val targetName: String?,
    val language: Language,
)

/** The default: no embellishment. The deterministic template line is used verbatim. */
object NoopEmbellisher : ChatEmbellisher {
    override suspend fun embellish(request: EmbellishRequest): String? = null
}
