package com.kursi.designsystem.audio

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

// ═══════════════════════════════════════════════════════════════════════════════
// SoundPlayer.jvm.kt — DESKTOP actual. javax.sound.sampled Clip playback from the bundled
// composeResources bytes.
//
// The vanilla JDK ships no Ogg Vorbis decoder — AudioSystem.getAudioInputStream() throws
// UnsupportedAudioFileException for the bundled .ogg clips absent a codec SPI on the classpath
// (none is added here: no new dependency, per the audio-layer guardrails). Desktop therefore
// currently degrades gracefully to a silent no-op via the runCatching below; the pipeline itself
// is complete and correct — dropping in a WAV/PCM clip (or an SPI jar) makes this target audible
// with no code change. See docs/experience-assets.md §3.
// ═══════════════════════════════════════════════════════════════════════════════

actual class SoundPlayer actual constructor() {
    private val clipBytes = mutableMapOf<KursiSound, ByteArray>()

    @Volatile
    private var released = false

    actual suspend fun play(sound: KursiSound) {
        if (released) return
        runCatching {
            val bytes = clipBytes.getOrPut(sound) { loadKursiSoundBytes(sound) ?: return@runCatching }
            AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { stream ->
                val clip = AudioSystem.getClip()
                clip.open(stream)
                clip.addLineListener { event -> if (event.type == LineEvent.Type.STOP) clip.close() }
                clip.start()
            }
        }
    }

    actual fun release() {
        released = true
        clipBytes.clear()
    }
}
