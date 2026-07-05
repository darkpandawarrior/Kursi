package com.kursi.ai

import com.kursi.engine.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BluffOddsTest {
    private fun estimate(
        role: Role = Role.NETA,
        copiesPerRole: Int = 3,
        deckSize: Int = 15,
        eliminatedForRole: Int = 0,
        myHandHasRole: Int = 0,
        oppFaceDown: Int = 2,
        totalVisible: Int = 0,
    ) = BluffOdds.estimate(
        claimedRole = role,
        copiesPerRole = copiesPerRole,
        deckSize = deckSize,
        eliminatedRolesForClaimedRole = eliminatedForRole,
        myHandContainsClaimedRole = myHandHasRole,
        opponentFaceDownCount = oppFaceDown,
        totalVisibleCards = totalVisible,
    )

    @Test
    fun allCopiesEliminated_pips5_obviousBluff() {
        val conf = estimate(eliminatedForRole = 3, totalVisible = 3)
        assertEquals(5, conf.pips)
    }

    @Test
    fun holdingClaimedRoleMyself_higherSuspicion() {
        val withoutMe = estimate(myHandHasRole = 0)
        val withMe = estimate(myHandHasRole = 2)
        // When I hold copies, fewer unseen → higher bluff probability
        assertTrue(withMe.pips >= withoutMe.pips)
    }

    @Test
    fun freshGame_allCopiesUnseen_notMaxSuspicion() {
        val conf = estimate() // default: 3 copies, none eliminated
        assertTrue(conf.pips <= 4, "Expected pips <= 4 for uncontested fresh game, got ${conf.pips}")
    }

    @Test
    fun opponentHasZeroCards_obviousBluff() {
        val conf = estimate(oppFaceDown = 0)
        assertEquals(5, conf.pips)
    }

    @Test
    fun labelAndWhisperNonEmpty() {
        val conf = estimate()
        assertTrue(conf.label.isNotBlank())
        assertTrue(conf.whisper.isNotBlank())
    }

    @Test
    fun pipsAlwaysInRange() {
        listOf(0, 1, 2, 3).forEach { elim ->
            listOf(0, 1, 2).forEach { myHand ->
                val conf = estimate(eliminatedForRole = elim, myHandHasRole = myHand.coerceAtMost(3 - elim))
                assertTrue(conf.pips in 1..5, "pips ${conf.pips} out of range for elim=$elim hand=$myHand")
            }
        }
    }
}
