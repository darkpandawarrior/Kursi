package com.kursi.designsystem.audio

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke test for the audio pipeline (docs/experience-assets.md §3): every clip in the finalized
 * manifest resolves to real, non-empty bytes from composeResources, and constructing + playing
 * through a [SoundPlayer] never throws. On JVM this also exercises the graceful-degradation path
 * (javax.sound.sampled has no Ogg Vorbis decoder without a codec SPI — see SoundPlayer.jvm.kt —
 * so play() here silently no-ops rather than producing audible output; that's the documented,
 * intended behaviour, not a test bug).
 */
class SoundPlayerTest {
    @Test
    fun everyClipInTheManifest_resolvesToNonEmptyBytes() =
        runBlocking {
            KursiSound.entries.forEach { sound ->
                val bytes = loadKursiSoundBytes(sound)
                assertNotNull(bytes, "missing clip resource for $sound (${sound.fileName})")
                assertTrue(bytes.isNotEmpty(), "empty clip resource for $sound (${sound.fileName})")
            }
        }

    @Test
    fun soundPlayer_playsEveryClip_withoutThrowing() =
        runBlocking {
            val player = SoundPlayer()
            KursiSound.entries.forEach { sound -> player.play(sound) }
            player.release()
        }
}
