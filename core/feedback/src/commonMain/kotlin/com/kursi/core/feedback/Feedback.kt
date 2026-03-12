package com.kursi.core.feedback

// ═══════════════════════════════════════════════════════════════════════════════
// Feedback.kt — the platform-agnostic feedback contract (M3).
//
// A tiny expect/actual layer so every moment in the stamp-theatre can fire a short
// SFX + a haptic pulse, gated by the master sound toggle (AppPrefs.soundFlow).
//
//  - SoundKey:      WHAT to play (stamp thud, coin clink, win sting, bark/win, …).
//  - HapticPattern: HOW the device should buzz (maps 1:1 onto the moment HapticBeat).
//  - SoundPlayer:   expect interface — actuals: jvm (javax.sound, real tone synth),
//                   android (SoundPool + Vibrator), ios (AVAudioPlayer + Impact gen),
//                   wasm (Web Audio / safe no-op).
//  - rememberDefaultSoundPlayer / defaultSoundPlayer(): platform factory.
//
// The :core:designsystem moment overlay owns the mapping from KursiMoment → SoundKey
// and HapticBeat → HapticPattern; this module stays free of any Compose / engine code.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The short SFX vocabulary used by the action-moment overlay.
 *
 * Each key is synthesised (jvm/ios tone synth) or sampled (android SoundPool) into a
 * sub-second sound. Keys are intentionally coarse — one per *feel*, not per moment —
 * so the whole game ships with a handful of audibly distinct cues.
 */
enum class SoundKey {
    /** Rubber-stamp slam — claims/blocks/reveals press a glyph onto the felt. */
    Stamp,

    /** Coin clink — economic actions (income / aid / tax) where khokhas travel. */
    Coin,

    /** Heavy thud — steals, supari, influence loss; weightier than a stamp. */
    Thud,

    /** Triumphant sting / "bark" — the win fanfare. */
    Win,
}

/**
 * Device haptic pattern. A 1:1 mirror of the moment-layer `HapticBeat` taxonomy so the
 * overlay can translate without this module importing :core:designsystem.
 */
enum class HapticPattern {
    /** No buzz. */
    None,

    /** A single light tick — routine economic actions, turn handoffs. */
    Tick,

    /** A single firm thud — steals, assassinations, influence loss. */
    Thud,

    /** Two quick buzzes — "caught lying" reveal. */
    DoubleBuzz,

    /** One long heavy pulse — reserved for Coup, Elimination, Win. */
    HeavyLong,
}

/**
 * Plays short SFX by [SoundKey] and fires haptics by [HapticPattern].
 *
 * Contract:
 *  - All methods are fire-and-forget and must never throw (a missing audio device,
 *    a denied haptic permission, or a headless CI box must degrade to a silent no-op).
 *  - [playSound] / [haptic] are only ever called by the overlay when the master sound
 *    toggle is ON; implementations need not re-check a preference.
 *  - [release] frees native resources (SoundPool, audio lines). Idempotent.
 *
 * The jvm actual genuinely emits audio (a tiny javax.sound tone synth) so the desktop
 * build is verifiably audible; the wasm actual uses Web Audio where available and
 * otherwise degrades to a no-op.
 */
interface SoundPlayer {
    /** Play the SFX mapped to [key]. No-op if audio is unavailable. */
    fun playSound(key: SoundKey)

    /** Fire a device haptic. No-op on platforms with no vibrator (desktop/web). */
    fun haptic(pattern: HapticPattern)

    /** Release native resources. Safe to call more than once. */
    fun release()
}

/**
 * Platform factory for the default [SoundPlayer].
 *
 * Implemented per target:
 *  - jvm:     real javax.sound.sampled tone synth (audible on desktop).
 *  - android: SoundPool + Vibrator (needs a Context — see the Android actual).
 *  - ios:     AVAudioPlayer + UIImpactFeedbackGenerator.
 *  - wasm:    Web Audio oscillator, or a no-op if AudioContext is unavailable.
 */
expect fun defaultSoundPlayer(): SoundPlayer
