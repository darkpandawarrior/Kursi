package com.kursi.feature.game

import com.kursi.engine.Action
import com.kursi.engine.CardId
import com.kursi.engine.GameEvent
import com.kursi.engine.LossReason
import com.kursi.engine.PlayerId
import com.kursi.engine.Role
import kotlin.test.Test
import kotlin.test.assertEquals

private fun sampleInfluenceLost() =
    GameEvent.InfluenceLost(
        player = PlayerId(0),
        card = CardId(0),
        role = Role.NETA,
        reason = LossReason.LOST_CHALLENGE,
    )

private fun sampleActionDeclared() =
    GameEvent.ActionDeclared(
        actor = PlayerId(0),
        action = Action.Tax,
        claimedRole = Role.NETA,
    )

class BeatGateTest {
    @Test
    fun tierFor_dramatic_beats() {
        assertEquals(BeatTier.DRAMATIC, tierFor(listOf(sampleInfluenceLost())))
    }

    @Test
    fun tierFor_routine_beats() {
        assertEquals(BeatTier.ROUTINE, tierFor(listOf(sampleActionDeclared())))
    }

    @Test
    fun tierFor_trivial_when_no_notable_events() {
        assertEquals(BeatTier.TRIVIAL, tierFor(emptyList()))
    }
}
