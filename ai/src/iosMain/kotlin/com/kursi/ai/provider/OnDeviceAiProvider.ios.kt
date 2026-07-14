package com.kursi.ai.provider

import com.siddharth.kmp.ai.CompositeOnDeviceLlm
import com.siddharth.kmp.ai.FoundationModelsOnDeviceLlm
import com.siddharth.kmp.ai.MediaPipeOnDeviceLlm
import com.siddharth.kmp.ai.OnDeviceLlm

/**
 * iOS on-device LLM tier (consolidation #7): routes through toolkit `:ai`'s Foundation Models →
 * MediaPipe chain. Both backends are stubs pending a Swift bridge (see toolkit's own KDoc on
 * FoundationModelsOnDeviceLlm/MediaPipeOnDeviceLlm) — same always-unavailable behavior as Kursi's old
 * local stub, now sourced from the shared toolkit instead of a duplicate.
 */
actual class OnDeviceAiProvider actual constructor() : AiProvider {
    override val id = "on_device"
    override val displayName = "On-device AI (Apple Intelligence)"

    private val llm: OnDeviceLlm = CompositeOnDeviceLlm(listOf(FoundationModelsOnDeviceLlm(), MediaPipeOnDeviceLlm()))

    override suspend fun isAvailable(): Boolean = llm.isAvailable()

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): String = llm.generate(messages.toOnDevicePrompt()) ?: ""
}
