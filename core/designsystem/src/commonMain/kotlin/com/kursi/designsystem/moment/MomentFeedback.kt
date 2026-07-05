package com.kursi.designsystem.moment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.kursi.core.feedback.HapticPattern
import com.kursi.core.feedback.SoundKey
import com.kursi.core.feedback.SoundPlayer
import com.kursi.core.feedback.defaultSoundPlayer

// ═══════════════════════════════════════════════════════════════════════════════
// MomentFeedback.kt — M3 glue between the moment data model and the :core:feedback
// expect/actual layer. Maps each KursiMoment to its SFX [SoundKey] and translates the
// moment's [HapticBeat] taxonomy to the platform [HapticPattern].
//
// The overlay fires these GATED by the master sound toggle (AppPrefs.soundFlow); when
// the toggle is off the overlay never calls in here, so the game is fully silent.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The SFX cue for a moment. Coins for economic actions, a heavy thud for losses/steals,
 * the win sting for victory, and a rubber-stamp slam for everything claim/reveal-shaped.
 */
internal fun KursiMoment.soundKey(): SoundKey =
    when (this) {
        is KursiMoment.Income,
        is KursiMoment.ForeignAid,
        is KursiMoment.Tax,
        -> SoundKey.Coin

        is KursiMoment.Steal,
        is KursiMoment.Assassinate,
        is KursiMoment.InfluenceLoss,
        is KursiMoment.Coup,
        is KursiMoment.Elimination,
        -> SoundKey.Thud

        is KursiMoment.Win -> SoundKey.Win

        is KursiMoment.Exchange,
        is KursiMoment.Block,
        is KursiMoment.Challenge,
        is KursiMoment.Reveal,
        is KursiMoment.TurnHandoff,
        -> SoundKey.Stamp
    }

/** Translate the moment-layer [HapticBeat] taxonomy into the device [HapticPattern]. */
internal fun HapticBeat.toPattern(): HapticPattern =
    when (this) {
        HapticBeat.None -> HapticPattern.None
        HapticBeat.Tick -> HapticPattern.Tick
        HapticBeat.Thud -> HapticPattern.Thud
        HapticBeat.DoubleBuzz -> HapticPattern.DoubleBuzz
        HapticBeat.HeavyLong -> HapticPattern.HeavyLong
    }

/**
 * Remembers a platform [SoundPlayer] for the lifetime of the composition and releases its
 * native resources (audio lines / SoundPool) on dispose.
 */
@Composable
fun rememberSoundPlayer(): SoundPlayer {
    val player = remember { defaultSoundPlayer() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

/**
 * Fires the SFX + haptic for [moment] through [player]. Caller is responsible for the
 * sound-enabled gate; this function assumes feedback is permitted.
 */
internal fun SoundPlayer.fire(moment: KursiMoment) {
    playSound(moment.soundKey())
    haptic(moment.haptic.toPattern())
}
