package com.kursi.ai

import com.kursi.engine.*

class LocalBrain(
    override val persona: Persona,
    private val policy: Policy,
    private val barker: (PlayerView, Intent) -> String? = { _, _ -> null },
) : BotBrain {
    override suspend fun decide(
        view: PlayerView,
        legal: List<Intent>,
        memory: BotMemory,
    ): Decision {
        val intent = policy.decide(view, legal)
        return Decision(intent, bark = barker(view, intent))
    }
}
