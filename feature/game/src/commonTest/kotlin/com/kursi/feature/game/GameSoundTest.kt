package com.kursi.feature.game

import com.kursi.designsystem.audio.KursiSound
import com.kursi.engine.Action
import com.kursi.engine.CardId
import com.kursi.engine.GameEvent
import com.kursi.engine.LossReason
import com.kursi.engine.PlayerId
import com.kursi.engine.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Locks in the pure GameEvent -> KursiSound mapping (docs/experience-assets.md §1 beat -> sound
 * map). Every case here mirrors a non-null branch of [mapEventToMoment] one-for-one (same events
 * drive both the visual moment and the SFX), plus the events that have no clip in the finalized
 * 17-clip manifest.
 */
class GameSoundTest {
    private val actor = PlayerId(0)
    private val target = PlayerId(1)

    @Test
    fun income_playsCoinSingle() {
        assertEquals(KursiSound.CoinSingle, GameEvent.ActionDeclared(actor, Action.Income, Role.NETA).toKursiSound())
    }

    @Test
    fun foreignAid_playsCoinDouble() {
        assertEquals(KursiSound.CoinDouble, GameEvent.ActionDeclared(actor, Action.ForeignAid, null).toKursiSound())
    }

    @Test
    fun tax_playsCoinCascade() {
        assertEquals(KursiSound.CoinCascade, GameEvent.ActionDeclared(actor, Action.Tax, Role.NETA).toKursiSound())
    }

    @Test
    fun steal_playsCoinSwipe() {
        assertEquals(KursiSound.CoinSwipe, GameEvent.ActionDeclared(actor, Action.Steal(target), Role.BABU).toKursiSound())
    }

    @Test
    fun exchange_playsCardDeal() {
        assertEquals(KursiSound.CardDeal, GameEvent.ActionDeclared(actor, Action.Exchange, Role.JUGAADU).toKursiSound())
    }

    @Test
    fun assassinate_playsImpactBlade() {
        assertEquals(
            KursiSound.ImpactBlade,
            GameEvent.ActionDeclared(actor, Action.Assassinate(target), Role.BHAI).toKursiSound(),
        )
    }

    @Test
    fun coup_playsImpactGavel() {
        assertEquals(KursiSound.ImpactGavel, GameEvent.ActionDeclared(actor, Action.Coup(target), null).toKursiSound())
    }

    @Test
    fun investigate_hasNoClip() {
        assertNull(GameEvent.ActionDeclared(actor, Action.Investigate(target), Role.VAKIL).toKursiSound())
    }

    @Test
    fun variantActions_haveNoClip() {
        assertNull(GameEvent.ActionDeclared(actor, Action.BailPe, null).toKursiSound())
        assertNull(GameEvent.ActionDeclared(actor, Action.Sabotage, null).toKursiSound())
        assertNull(GameEvent.ActionDeclared(actor, Action.Emergency, null).toKursiSound())
    }

    @Test
    fun challengeDeclared_playsStampSlam() {
        assertEquals(KursiSound.StampSlam, GameEvent.Challenged(actor, target, Role.NETA).toKursiSound())
    }

    @Test
    fun challengeRevealed_true_playsStingTrue() {
        assertEquals(
            KursiSound.StingTrue,
            GameEvent.ChallengeRevealed(actor, CardId(0), Role.NETA, hadRole = true).toKursiSound(),
        )
    }

    @Test
    fun challengeRevealed_bluff_playsStingBluff() {
        assertEquals(
            KursiSound.StingBluff,
            GameEvent.ChallengeRevealed(actor, CardId(0), Role.NETA, hadRole = false).toKursiSound(),
        )
    }

    @Test
    fun influenceLost_playsCardPlaceHard() {
        assertEquals(
            KursiSound.CardPlaceHard,
            GameEvent.InfluenceLost(actor, CardId(0), Role.NETA, LossReason.COUPED).toKursiSound(),
        )
    }

    @Test
    fun turnAdvanced_playsUiTap() {
        assertEquals(KursiSound.UiTap, GameEvent.TurnAdvanced(toSeat = 1, turnNumber = 2).toKursiSound())
    }

    @Test
    fun gameEnded_playsStingWin() {
        assertEquals(KursiSound.StingWin, GameEvent.GameEnded(actor).toKursiSound())
    }

    @Test
    fun blockedAndEliminated_haveNoClipInTheManifest() {
        assertNull(GameEvent.Blocked(actor, Role.NETA, Action.ForeignAid).toKursiSound())
        assertNull(GameEvent.PlayerEliminated(actor).toKursiSound())
    }
}
