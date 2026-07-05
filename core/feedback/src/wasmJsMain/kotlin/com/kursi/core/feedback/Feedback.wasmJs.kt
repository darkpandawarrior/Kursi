@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.kursi.core.feedback

// ═══════════════════════════════════════════════════════════════════════════════
// Feedback.wasmJs.kt — wasmJs actual.
//
// Sound:  a tiny Web Audio oscillator-beep via a @JsFun bridge. Browsers gate audio
//         behind a user gesture; the first beep after a click/tap unlocks the rest.
//         If AudioContext is unavailable the JS helper swallows the error → no-op.
// Haptic: navigator.vibrate where supported (mobile web); otherwise a no-op. Desktop
//         browsers have no vibration motor, so this degrades silently.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Plays a short decaying sine beep. All work is inside JS with a try/catch so a missing
 * AudioContext (or autoplay block before the first gesture) never propagates an exception.
 */
private fun jsBeep(
    freqHz: Double,
    durationMs: Double,
    amplitude: Double,
): Unit =
    js(
        """{
            try {
                var Ctx = window.AudioContext || window.webkitAudioContext;
                if (!Ctx) return;
                if (!window.__kursiAudioCtx) { window.__kursiAudioCtx = new Ctx(); }
                var ctx = window.__kursiAudioCtx;
                if (ctx.state === 'suspended') { ctx.resume(); }
                var osc = ctx.createOscillator();
                var gain = ctx.createGain();
                osc.frequency.value = freqHz;
                osc.type = 'sine';
                var now = ctx.currentTime;
                gain.gain.setValueAtTime(amplitude, now);
                gain.gain.exponentialRampToValueAtTime(0.0001, now + durationMs / 1000.0);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + durationMs / 1000.0);
            } catch (e) { /* no-op: audio unavailable */ }
        }""",
    )

/** Fires navigator.vibrate(ms) where supported. Swallows any error. */
private fun jsVibrate(ms: Int): Unit =
    js(
        """{
            try {
                if (typeof navigator !== 'undefined' && navigator.vibrate) {
                    navigator.vibrate(ms);
                }
            } catch (e) { /* no-op */ }
        }""",
    )

private class WasmSoundPlayer : SoundPlayer {
    override fun playSound(key: SoundKey) {
        when (key) {
            SoundKey.Stamp -> jsBeep(196.0, 120.0, 0.4)
            SoundKey.Coin -> jsBeep(1320.0, 90.0, 0.3)
            SoundKey.Thud -> jsBeep(110.0, 160.0, 0.45)
            SoundKey.Win -> {
                // Rising three-note sting (best-effort; staggered by the browser clock).
                jsBeep(523.25, 110.0, 0.35)
                jsBeep(659.25, 110.0, 0.35)
                jsBeep(783.99, 220.0, 0.4)
            }
        }
    }

    override fun haptic(pattern: HapticPattern) {
        val ms =
            when (pattern) {
                HapticPattern.None -> return
                HapticPattern.Tick -> 18
                HapticPattern.Thud -> 45
                HapticPattern.DoubleBuzz -> 90
                HapticPattern.HeavyLong -> 120
            }
        jsVibrate(ms)
    }

    override fun release() {
        // Nothing to free — the shared AudioContext lives on window for reuse.
    }
}

actual fun defaultSoundPlayer(): SoundPlayer = WasmSoundPlayer()
