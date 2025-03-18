package com.kursi.core.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// Feedback.android.kt — ANDROID actual.
//
// Sound: synthesised PCM streamed on a short-lived AudioTrack (no bundled assets and
//        no Context required, so the no-arg factory works from any module). Where the
//        host app installs an application Context via FeedbackAndroid.install(context),
//        haptics route through the real system Vibrator.
//
// Haptic: real Vibrator (VibrationEffect on API 26+) when a Context has been installed;
//         a safe no-op otherwise.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Optional install hook so the Android app shell can hand us an application Context
 * (needed for the system Vibrator). Sound works without it; haptics no-op until set.
 *
 * Wire from the Android Application/Activity:
 * ```kotlin
 * FeedbackAndroid.install(applicationContext)
 * ```
 */
object FeedbackAndroid {
    @Volatile internal var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }
}

private const val SAMPLE_RATE = 44_100

private fun renderTone(freqHz: Double, durMs: Int, decay: Double, amplitude: Double = 0.45): ShortArray {
    val n = (SAMPLE_RATE.toLong() * durMs / 1000L).toInt().coerceAtLeast(1)
    val out = ShortArray(n)
    for (i in 0 until n) {
        val t = i / SAMPLE_RATE.toDouble()
        val env = exp(-decay * t)
        val sample = sin(2.0 * PI * freqHz * t) * env * amplitude
        out[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
    }
    return out
}

private fun renderWinSting(): ShortArray {
    val notes = listOf(
        renderTone(523.25, 110, 9.0, 0.4),
        renderTone(659.25, 110, 9.0, 0.4),
        renderTone(783.99, 220, 7.0, 0.45),
    )
    val out = ShortArray(notes.sumOf { it.size })
    var pos = 0
    for (n in notes) {
        n.copyInto(out, pos)
        pos += n.size
    }
    return out
}

private class AndroidSoundPlayer : SoundPlayer {

    private val samples: Map<SoundKey, ShortArray> = mapOf(
        SoundKey.Stamp to renderTone(196.0, 120, 26.0, 0.5),
        SoundKey.Coin to renderTone(1320.0, 90, 28.0, 0.35),
        SoundKey.Thud to renderTone(110.0, 160, 14.0, 0.55),
        SoundKey.Win to renderWinSting(),
    )

    @Volatile private var released = false

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? by lazy {
        val ctx = FeedbackAndroid.appContext ?: return@lazy null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun playSound(key: SoundKey) {
        if (released) return
        val pcm = samples[key] ?: return
        thread(isDaemon = true, name = "kursi-sfx-${key.name}") {
            runCatching {
                val bytes = pcm.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(bytes.coerceAtLeast(512))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // Hold briefly so STATIC playback finishes before release.
                Thread.sleep((pcm.size * 1000L / SAMPLE_RATE) + 60L)
                track.stop()
                track.release()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun haptic(pattern: HapticPattern) {
        if (released || pattern == HapticPattern.None) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (pattern) {
                    HapticPattern.Tick -> VibrationEffect.createOneShot(18, 90)
                    HapticPattern.Thud -> VibrationEffect.createOneShot(45, 180)
                    HapticPattern.DoubleBuzz ->
                        VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), -1)
                    HapticPattern.HeavyLong ->
                        VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticPattern.None -> return
                }
                v.vibrate(effect)
            } else {
                val ms = when (pattern) {
                    HapticPattern.Tick -> 18L
                    HapticPattern.Thud -> 45L
                    HapticPattern.DoubleBuzz -> 90L
                    HapticPattern.HeavyLong -> 120L
                    HapticPattern.None -> 0L
                }
                if (ms > 0) v.vibrate(ms)
            }
        }
    }

    override fun release() {
        released = true
    }
}

actual fun defaultSoundPlayer(): SoundPlayer = AndroidSoundPlayer()
