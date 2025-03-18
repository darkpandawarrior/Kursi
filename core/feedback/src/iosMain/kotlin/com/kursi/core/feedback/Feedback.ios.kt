@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.kursi.core.feedback

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

// ═══════════════════════════════════════════════════════════════════════════════
// Feedback.ios.kt — iOS actual.
//
// Sound:  synthesise a 16-bit PCM WAV in memory, wrap it as NSData, and play through
//         AVAudioPlayer. No bundled assets needed.
// Haptic: UIImpactFeedbackGenerator (light/medium/heavy) + UINotificationFeedbackGenerator
//         (for the "caught lying" double buzz → .warning).
// ═══════════════════════════════════════════════════════════════════════════════

private const val SAMPLE_RATE = 44_100

private fun appendLE(out: MutableList<Byte>, value: Int, bytes: Int) {
    var v = value
    repeat(bytes) {
        out.add((v and 0xFF).toByte())
        v = v shr 8
    }
}

/** Build a complete mono 16-bit PCM WAV file (header + data) from synthesised samples. */
private fun renderWav(build: (i: Int, sr: Double) -> Double, durMs: Int): ByteArray {
    val n = (SAMPLE_RATE.toLong() * durMs / 1000L).toInt().coerceAtLeast(1)
    val pcm = ArrayList<Byte>(n * 2 + 44)

    val byteRate = SAMPLE_RATE * 2
    val dataSize = n * 2

    // RIFF header
    "RIFF".forEach { pcm.add(it.code.toByte()) }
    appendLE(pcm, 36 + dataSize, 4)
    "WAVE".forEach { pcm.add(it.code.toByte()) }
    // fmt chunk
    "fmt ".forEach { pcm.add(it.code.toByte()) }
    appendLE(pcm, 16, 4)            // chunk size
    appendLE(pcm, 1, 2)            // PCM
    appendLE(pcm, 1, 2)            // mono
    appendLE(pcm, SAMPLE_RATE, 4)
    appendLE(pcm, byteRate, 4)
    appendLE(pcm, 2, 2)            // block align
    appendLE(pcm, 16, 2)           // bits per sample
    // data chunk
    "data".forEach { pcm.add(it.code.toByte()) }
    appendLE(pcm, dataSize, 4)

    for (i in 0 until n) {
        val sample = build(i, SAMPLE_RATE.toDouble())
        val s = (sample * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
        appendLE(pcm, s and 0xFFFF, 2)
    }
    return pcm.toByteArray()
}

private fun tone(freqHz: Double, decay: Double, amplitude: Double): (Int, Double) -> Double =
    { i, sr ->
        val t = i / sr
        sin(2.0 * PI * freqHz * t) * exp(-decay * t) * amplitude
    }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

private class IosSoundPlayer : SoundPlayer {

    private val wavs: Map<SoundKey, ByteArray> = mapOf(
        SoundKey.Stamp to renderWav(tone(196.0, 26.0, 0.5), 120),
        SoundKey.Coin to renderWav(tone(1320.0, 28.0, 0.35), 90),
        SoundKey.Thud to renderWav(tone(110.0, 14.0, 0.55), 160),
        SoundKey.Win to renderWav(tone(659.25, 8.0, 0.45), 320),
    )

    // Retain players so they are not GC'd mid-playback.
    private val active = mutableListOf<AVAudioPlayer>()

    private var released = false

    init {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryAmbient, null)
            session.setActive(true, null)
        }
    }

    override fun playSound(key: SoundKey) {
        if (released) return
        val data = wavs[key] ?: return
        runCatching {
            val player = AVAudioPlayer(data = data.toNSData(), error = null)
            player.prepareToPlay()
            player.play()
            active.add(player)
            if (active.size > 8) active.removeAt(0)
        }
    }

    override fun haptic(pattern: HapticPattern) {
        if (released || pattern == HapticPattern.None) return
        runCatching {
            when (pattern) {
                HapticPattern.Tick -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).apply {
                    prepare(); impactOccurred()
                }
                HapticPattern.Thud -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).apply {
                    prepare(); impactOccurred()
                }
                HapticPattern.HeavyLong -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).apply {
                    prepare(); impactOccurred()
                }
                HapticPattern.DoubleBuzz -> UINotificationFeedbackGenerator().apply {
                    prepare()
                    notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
                }
                HapticPattern.None -> Unit
            }
        }
    }

    override fun release() {
        released = true
        active.clear()
    }
}

actual fun defaultSoundPlayer(): SoundPlayer = IosSoundPlayer()
