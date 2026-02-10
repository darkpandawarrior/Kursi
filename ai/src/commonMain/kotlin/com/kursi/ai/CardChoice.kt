package com.kursi.ai

import com.kursi.engine.*

/**
 * Shared, secrecy-safe helpers for the two phases where a policy must address a *specific*
 * viewer-owned [CardId] rather than a public role: Exchange (keep-set selection) and
 * InfluenceLoss (which card to shed).
 *
 * Both resolve [CardId] → [Role] strictly through [PlayerView.myCards] (the viewer's OWN cards only)
 * and, for Exchange, [PhaseView.Exchange.drawn] (the actor's OWN drawn cards). No opponent identity
 * ever enters here, so the secrecy boundary is preserved.
 *
 * The canonical [roleValue] ranking (NETA > BABU > PATRAKAAR > BHAI > VAKIL > JUGAADU) is the single
 * source of truth reused by every policy tier. Note that [Role.ordinal] order (NETA, BHAI, BABU,
 * JUGAADU, VAKIL, PATRAKAAR) is NOT value order — that mismatch is exactly the historical decideLoss
 * bug this helper fixes.
 */
object CardChoice {

    /**
     * Higher = more valuable to keep. The one ranking every tier scores against.
     * PATRAKAAR (Inquisitor / Jaanch): info-then-disrupt with no own counter — slotted just under BABU.
     */
    val roleValue: Map<Role, Int> = mapOf(
        Role.NETA to 6,
        Role.BABU to 5,
        Role.PATRAKAAR to 4,
        Role.BHAI to 3,
        Role.VAKIL to 2,
        Role.JUGAADU to 1,
    )

    fun value(role: Role): Int = roleValue[role] ?: 0

    /**
     * Resolve every CardId the viewer is entitled to address in an Exchange to its role:
     * the viewer's own FACE-DOWN influence (from [PlayerView.myCards]) plus the drawn cards
     * (from [PhaseView.Exchange.drawn]). Face-up cards are excluded — they can never be kept.
     */
    fun exchangeRoleOf(view: PlayerView): Map<CardId, Role> {
        val phase = view.phase as? PhaseView.Exchange ?: return emptyMap()
        val map = HashMap<CardId, Role>()
        for (c in view.myCards) if (!c.faceUp) map[c.id] = c.role
        for (c in phase.drawn) map[c.id] = c.role
        return map
    }

    /** Sum of [roleValue] over a keep-set, resolved via [roleOf]. Unknown CardIds contribute 0. */
    fun keepValue(keep: List<CardId>, roleOf: Map<CardId, Role>): Int =
        keep.sumOf { value(roleOf[it] ?: return@sumOf 0) }

    /**
     * The role-optimal [Intent.ChooseExchange]: the legal keep-set with the highest summed role value.
     * Ties broken deterministically by the engine's enumeration order (stable [maxByOrNull]).
     * Returns null only if there are no ChooseExchange intents (caller should fall back).
     */
    fun bestExchange(view: PlayerView, legal: List<Intent>): Intent.ChooseExchange? {
        val choices = legal.filterIsInstance<Intent.ChooseExchange>()
        if (choices.isEmpty()) return null
        val roleOf = exchangeRoleOf(view)
        return choices.maxByOrNull { keepValue(it.keep, roleOf) }
    }

    /**
     * The [Intent.ChooseInfluenceToLose] that sheds the LOWEST-value actual role.
     * Resolves each candidate CardId to its true role via [PlayerView.myCards] (face-down only),
     * fixing the role-ordinal-vs-CardId index bug. Returns null if there are no loss intents.
     */
    fun worstLoss(view: PlayerView, legal: List<Intent>): Intent.ChooseInfluenceToLose? {
        val losses = legal.filterIsInstance<Intent.ChooseInfluenceToLose>()
        if (losses.isEmpty()) return null
        val roleOf = view.myCards.filter { !it.faceUp }.associate { it.id to it.role }
        // Shed the lowest-value role; if a CardId is somehow unresolved, treat as max value so we don't
        // accidentally discard our best card on bad data.
        return losses.minByOrNull { roleValue[roleOf[it.card]] ?: Int.MAX_VALUE }
    }
}
