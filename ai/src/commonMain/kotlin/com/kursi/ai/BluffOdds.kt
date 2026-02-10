package com.kursi.ai

import com.kursi.engine.Role
import kotlin.math.pow

/**
 * BluffOdds — pure function estimating P(claim is a bluff) from visible public state.
 * No hidden info is used: only eliminated/revealed cards (faceUpRoles) and my own hand.
 *
 * Returns a [Confidence] level 1..5 where:
 *   1 = almost certainly honest (few unseen copies → they likely have it)
 *   5 = very likely a bluff (all copies accounted-for)
 * with a human-readable label for display in the brass odds chip.
 *
 * Algorithm: count unseenCopies for the claimed role after removing
 * confirmed-eliminated cards and the viewer's own hand.
 * Bluff probability ≈ 1 − P(at least 1 of opponent's k cards is that role).
 * P(slot = role) ≈ unseenCopies / totalUnseen
 * P(holds) = 1 − (1 − pSlot)^k
 *
 * This is public-info only: safe to show to the human player.
 */
object BluffOdds {

    /** 1 (very likely honest) .. 5 (very likely bluffing). */
    data class Confidence(
        val pips: Int,          // 1..5
        val label: String,      // e.g. "long shot", "coin-flip", "likely honest"
        val whisper: String,    // e.g. "2 of 3 NETA unseen → likely honest"
    )

    /**
     * @param claimedRole the role being claimed
     * @param copiesPerRole total copies of each role in the deck (from GameConfig)
     * @param deckSize total deck size
     * @param eliminatedRolesForClaimedRole how many cards of [claimedRole] are face-up eliminated
     * @param myHandContainsClaimedRole how many of [claimedRole] I personally hold
     * @param opponentFaceDownCount how many face-down cards the claiming opponent has (k)
     * @param totalVisibleCards total face-up cards (all players, all roles)
     */
    fun estimate(
        claimedRole: Role,
        copiesPerRole: Int,
        deckSize: Int,
        eliminatedRolesForClaimedRole: Int,
        myHandContainsClaimedRole: Int,
        opponentFaceDownCount: Int,
        totalVisibleCards: Int,
    ): Confidence {
        val unseenForRole = (copiesPerRole - eliminatedRolesForClaimedRole - myHandContainsClaimedRole)
            .coerceAtLeast(0)
        val totalUnseen = (deckSize - totalVisibleCards).coerceAtLeast(1)
        val k = opponentFaceDownCount.coerceAtLeast(0)

        if (k == 0) {
            return Confidence(
                pips = 5,
                label = "obvious bluff",
                whisper = "They have no cards. Impossible claim.",
            )
        }

        val pSlot = if (totalUnseen > 0 && unseenForRole > 0)
            unseenForRole.toDouble() / totalUnseen
        else 0.0

        val pHolds = if (pSlot > 0.0) 1.0 - (1.0 - pSlot).pow(k.toDouble()) else 0.0
        val pBluff = (1.0 - pHolds).coerceIn(0.0, 1.0)

        val roleName = claimedRole.name

        // Map bluff probability to 1..5 pips
        val pips = when {
            pBluff < 0.20 -> 1
            pBluff < 0.38 -> 2
            pBluff < 0.55 -> 3
            pBluff < 0.72 -> 4
            else -> 5
        }

        val label = when (pips) {
            1 -> "likely honest"
            2 -> "probably real"
            3 -> "coin-flip"
            4 -> "probably bluffing"
            else -> "long shot"
        }

        // Build a context prefix so the viewer sees what's already accounted for.
        // e.g. "you hold 1 · 1 eliminated · " or just "1 eliminated · "
        val accountedParts = buildList {
            if (myHandContainsClaimedRole > 0) add("you hold $myHandContainsClaimedRole")
            if (eliminatedRolesForClaimedRole > 0) add("$eliminatedRolesForClaimedRole eliminated")
        }
        val accountedPrefix = if (accountedParts.isNotEmpty()) accountedParts.joinToString(" · ") + " · " else ""

        val whisper = when {
            unseenForRole == 0 ->
                "${accountedPrefix}all $copiesPerRole $roleName accounted for — almost certainly bluffing."
            pips <= 2 ->
                "${accountedPrefix}$unseenForRole of $copiesPerRole $roleName unaccounted — they probably have it."
            pips == 3 ->
                "${accountedPrefix}$unseenForRole of $copiesPerRole $roleName unaccounted — roughly a coin-flip."
            else ->
                "${accountedPrefix}$unseenForRole of $copiesPerRole $roleName unaccounted — odds favour a bluff."
        }

        return Confidence(pips = pips, label = label, whisper = whisper)
    }
}
