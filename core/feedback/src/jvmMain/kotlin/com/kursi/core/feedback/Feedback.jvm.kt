package com.kursi.core.feedback

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// Feedback.jvm.kt — DESKTOP actual. A tiny javax.sound.sampled tone synth so the
// jvm/desktop app is genuinely audible. No bundled assets: every SFX is synthesised
// PCM at startup (a few hundred bytes each) and streamed on a SourceDataLine.
//
// Desktops have no vibration motor, so haptic() is a no-op.
// ═══════════════════════════════════════════════════════════════════════════════

private const val SAMPLE_RATE = 44_100f

/**
 * Synthesises a short decaying sine "blip". [freqHz] sets the pitch; [durMs] the length;
 * [decay] how fast the exponential envelope falls (larger = snappier).
 */
private fun renderTone(
    freqHz: Double,
    durMs: Int,
    decay: Double,
    amplitude: Double = 0.45,
): ByteArray {
    val n = (SAMPLE_RATE * durMs / 1000f).toInt().coerceAtLeast(1)
    val buf = ByteArray(n * 2) // 16-bit mono, little-endian
    for (i in 0 until n) {
        val t = i / SAMPLE_RATE.toDouble()
        val env = exp(-decay * t)
        val sample = sin(2.0 * PI * freqHz * t) * env * amplitude
        val s = (sample * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
        buf[i * 2] = (s and 0xFF).toByte()
        buf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
    }
    return buf
}

/** Two-layer "clink" — a bright tone plus a higher partial — for the coin SFX. */
private fun renderClink(): ByteArray {
    val a = renderTone(1320.0, 90, 28.0, 0.35)
    val b = renderTone(1980.0, 70, 36.0, 0.22)
    val out = ByteArray(maxOf(a.size, b.size))
    a.copyInto(out)
    // mix b on top (clamped)
    for (i in b.indices step 2) {
        if (i + 1 >= out.size) break
        val cur = ((out[i + 1].toInt() shl 8) or (out[i].toInt() and 0xFF)).toShort().toInt()
        val add = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt()
        val s = (cur + add).coerceIn(-32768, 32767)
        out[i] = (s and 0xFF).toByte()
        out[i + 1] = ((s shr 8) and 0xFF).toByte()
    }
    return out
}

private class JvmSoundPlayer : SoundPlayer {

    private val format = AudioFormat(SAMPLE_RATE, 16, 1, true, false)

    // Pre-rendered PCM, one buffer per key.
    private val samples: Map<SoundKey, ByteArray> = mapOf(
        SoundKey.Stamp to renderTone(196.0, 120, 26.0, 0.5),  // low woody slam
        SoundKey.Coin to renderClink(),                        // bright clink
        SoundKey.Thud to renderTone(110.0, 160, 14.0, 0.55),   // deep thud
        SoundKey.Win to renderWinSting(),                      // rising fanfare
    )

    @Volatile private var released = false

    override fun playSound(key: SoundKey) {
        if (released) return
        val pcm = samples[key] ?: return
        // Stream on a daemon thread so we never block composition / the render thread.
        thread(isDaemon = true, name = "kursi-sfx-${key.name}") {
            runCatching {
                val line = AudioSystem.getSourceDataLine(format)
                line.open(format)
                line.start()
                var off = 0
                while (off < pcm.size && !released) {
                    val wrote = line.write(pcm, off, pcm.size - off)
                    off += wrote
                }
                line.drain()
                line.stop()
                line.close()
            }
        }
    }

    override fun haptic(pattern: HapticPattern) {
        // No vibration motor on desktop — intentional no-op.
    }

    override fun release() {
        released = true
    }
}

/** A short rising three-note sting for the win fanfare. */
private fun renderWinSting(): ByteArray {
    val notes = listOf(
        renderTone(523.25, 110, 9.0, 0.4), // C5
        renderTone(659.25, 110, 9.0, 0.4), // E5
        renderTone(783.99, 220, 7.0, 0.45), // G5 (held)
    )
    val total = notes.sumOf { it.size }
    val out = ByteArray(total)
    var pos = 0
    for (note in notes) {
        note.copyInto(out, pos)
        pos += note.size
    }
    return out
}

actual fun defaultSoundPlayer(): SoundPlayer = JvmSoundPlayer()
