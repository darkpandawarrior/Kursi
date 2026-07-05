package com.kursi.ai.persona

import com.kursi.ai.EasyPolicy
import com.kursi.ai.ExpertPolicy
import com.kursi.ai.GRANDMASTER_DEFAULT_BUDGET
import com.kursi.ai.GrandmasterPolicy
import com.kursi.ai.HardPolicy
import com.kursi.ai.MediumPolicy
import com.kursi.ai.SearchBudget
import com.kursi.engine.PlayerId

/** Difficulty tiers available to the player. */
enum class BotDifficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT,
    GRANDMASTER,
}

/**
 * Assigns distinct [BotPersona]s to bot seats and wraps each in a [PersonaPolicy]
 * with the appropriate tier base policy.
 *
 * @param seatCount  Number of bot seats (≤ 9; capped to roster size).
 * @param difficulty Base tier for ALL bots.
 * @param seed       Deterministic shuffle seed — same seed produces the same roster every game.
 * @return Map from [PlayerId] to its [PersonaPolicy] (seats 1..seatCount-1 when human is seat 0,
 *         but callers decide seat mapping; this returns a list in order 0..<seatCount).
 */
object PersonaAssigner {
    /**
     * Strong in-game Expert budget: 8 000 iterations / 900 ms / horizon 16.
     * Wall-clock cap keeps it safe on slower machines; extra iterations and
     * deeper rollout horizon give noticeably stronger play vs the old 4k/700ms.
     * Distinct from test budgets (defined locally in test files) so CI stays fast.
     */
    val EXPERT_GAME_BUDGET =
        SearchBudget(
            maxMillis = 900L,
            maxIterations = 8000,
            rolloutHorizon = 16,
        )

    fun assign(
        seatCount: Int,
        difficulty: BotDifficulty,
        seed: Long,
    ): List<Pair<BotPersona, PersonaPolicy>> {
        require(seatCount in 1..PersonaRoster.ALL.size) {
            "seatCount $seatCount out of range 1..${PersonaRoster.ALL.size}"
        }

        // Deterministic shuffle using a simple LCG on the seed.
        val shuffled = PersonaRoster.ALL.shuffledDeterministic(seed)
        val chosen = shuffled.take(seatCount)

        // Optional hue-lightness nudge: if two personas sharing a seat color land at the same
        // table, the second gets a nudged ARGB (+0x10 on the blue/green channel lightness bump).
        // This is purely cosmetic — the policy math uses the original persona object.
        val seenColors = mutableSetOf<Long>()
        val result = mutableListOf<Pair<BotPersona, PersonaPolicy>>()
        chosen.forEachIndexed { i, persona ->
            val finalPersona =
                if (persona.seatColorArgb in seenColors) {
                    // Nudge lightness by brightening the persona's color slightly for disambiguation.
                    persona.copy(seatColorArgb = persona.seatColorArgb.nudgeLightness())
                } else {
                    persona
                }
            seenColors.add(finalPersona.seatColorArgb)

            val botSeed = seed * 31L + i
            val base =
                when (difficulty) {
                    BotDifficulty.EASY -> EasyPolicy(botSeed)
                    BotDifficulty.MEDIUM -> MediumPolicy(botSeed)
                    BotDifficulty.HARD -> HardPolicy(botSeed)
                    BotDifficulty.EXPERT -> ExpertPolicy(seed = botSeed, budget = EXPERT_GAME_BUDGET)
                    BotDifficulty.GRANDMASTER ->
                        GrandmasterPolicy(seed = botSeed, budget = GRANDMASTER_DEFAULT_BUDGET)
                }
            result.add(finalPersona to PersonaPolicy(finalPersona, base, seed = botSeed + 99L))
        }
        return result
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Deterministic Fisher-Yates shuffle using a LCG seeded from [seed]. */
    private fun <T> List<T>.shuffledDeterministic(seed: Long): List<T> {
        val list = toMutableList()
        var s = seed
        for (i in list.indices.reversed()) {
            s = s * 6364136223846793005L + 1442695040888963407L
            val j = ((s ushr 33) % (i + 1)).toInt().and(0x7fffffff) % (i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
        return list
    }

    /**
     * Bump the lightness of an ARGB Long by adding a small amount to the brightest channel.
     * This is purely visual disambiguation when two personas share a seat hue.
     */
    private fun Long.nudgeLightness(): Long {
        val a = (this shr 24) and 0xFFL
        val r = ((this shr 16) and 0xFFL) + 0x18L
        val g = ((this shr 8) and 0xFFL) + 0x18L
        val b = (this and 0xFFL) + 0x18L
        return (a shl 24) or
            (r.coerceAtMost(0xFFL) shl 16) or
            (g.coerceAtMost(0xFFL) shl 8) or
            b.coerceAtMost(0xFFL)
    }
}
