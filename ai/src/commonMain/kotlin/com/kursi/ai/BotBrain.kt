package com.kursi.ai

import com.kursi.engine.*

data class Persona(
    val id: String,
    val displayName: String,
    val aggression: Double = 0.5,
    val bluffiness: Double = 0.5,
    val vengefulness: Double = 0.5,
    val caution: Double = 0.5,
    val chattiness: Double = 0.5,
) {
    companion object {
        val DEFAULT = Persona("default", "Bot")
    }
}

data class Decision(
    val intent: Intent,
    val rationale: String? = null,
    val bark: String? = null,
)

interface BotBrain {
    val persona: Persona

    suspend fun decide(
        view: PlayerView,
        legal: List<Intent>,
        memory: BotMemory,
    ): Decision
}
