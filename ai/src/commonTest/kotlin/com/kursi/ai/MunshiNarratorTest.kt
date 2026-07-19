package com.kursi.ai

import com.kursi.engine.GameConfig
import com.kursi.engine.GameEvent
import com.kursi.engine.PlayerId
import com.kursi.engine.initialState
import com.kursi.engine.redact
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.llmchat.AiProviderConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A fake on-device slot that reports itself unavailable — the tier-1 "no real SDK wired" case. */
private class UnavailableProvider : AiProvider {
    override val id = "on_device"
    override val displayName = "fake on-device"

    override suspend fun isAvailable() = false

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ) = ""
}

/** A fake on-device slot that IS available and returns a fixed line — exercises the upgrade path. */
private class FakeAvailableProvider(
    private val response: String,
) : AiProvider {
    override val id = "on_device"
    override val displayName = "fake on-device"

    override suspend fun isAvailable() = true

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ) = response
}

class MunshiNarratorTest {
    private val config = GameConfig.forPlayers(2)
    private val state = initialState(config, seed = 1L)
    private val view = redact(state, PlayerId(0))
    private val events = listOf<GameEvent>(GameEvent.TurnAdvanced(toSeat = 0, turnNumber = 1))

    @Test
    fun narrate_returnsNull_whenNoOnDeviceAndNoByokKey_selectionFallsBackToTemplated() =
        runTest {
            val munshi = MunshiNarrator(cloudConfig = AiProviderConfig(useOnDevice = true), onDevice = UnavailableProvider())

            assertNull(munshi.narrate(view, events))
        }

    @Test
    fun narrate_returnsNull_whenUseOnDeviceFalse_evenIfProviderWouldBeAvailable() =
        runTest {
            // SELECTION POLICY (spec §8.5): on-device is auto-detected only when opted into; BYOK/on-device
            // are never silently used behind the caller's back.
            val munshi = MunshiNarrator(cloudConfig = AiProviderConfig(useOnDevice = false), onDevice = FakeAvailableProvider("hello"))

            assertNull(munshi.narrate(view, events))
        }

    @Test
    fun narrate_upgradesInPlace_whenOnDeviceIsAvailable() =
        runTest {
            val munshi =
                MunshiNarrator(
                    cloudConfig = AiProviderConfig(useOnDevice = true),
                    onDevice = FakeAvailableProvider("  Bahenji stamped the seal.  "),
                )

            assertEquals("Bahenji stamped the seal.", munshi.narrate(view, events))
        }

    @Test
    fun narrate_returnsNull_onBlankProviderResponse_neverAnEmptyUpgrade() =
        runTest {
            val munshi = MunshiNarrator(cloudConfig = AiProviderConfig(useOnDevice = true), onDevice = FakeAvailableProvider("   "))

            assertNull(munshi.narrate(view, events))
        }
}
