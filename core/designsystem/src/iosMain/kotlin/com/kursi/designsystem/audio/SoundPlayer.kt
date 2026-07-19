package com.kursi.designsystem.audio

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

// ═══════════════════════════════════════════════════════════════════════════════
// SoundPlayer.ios.kt — IOS actual. AVAudioPlayer fed from an in-memory NSData built from the
// bundled composeResources bytes.
//
// Core Audio (the codec AVAudioPlayer relies on) has no built-in Ogg Vorbis decoder, so
// AVAudioPlayer(data:fileTypeHint:) returns nil for the bundled .ogg clips on real iOS — this
// currently degrades gracefully to a silent no-op via the runCatching below, same shape as the
// jvm actual's missing-SPI case. Compile-verified only here (:core:designsystem klib compile);
// on-device/simulator audio verification is a separate step. See docs/experience-assets.md §3.
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalForeignApi::class)
actual class SoundPlayer actual constructor() {
    private val players = mutableMapOf<KursiSound, AVAudioPlayer>()

    actual suspend fun play(sound: KursiSound) {
        runCatching {
            val player =
                players.getOrPut(sound) {
                    val bytes = loadKursiSoundBytes(sound) ?: return@runCatching
                    AVAudioPlayer(data = bytes.toNSData(), fileTypeHint = "ogg", error = null)
                        .also { it.prepareToPlay() }
                }
            player.currentTime = 0.0
            player.play()
        }
    }

    actual fun release() {
        runCatching { players.values.forEach { it.stop() } }
        players.clear()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
