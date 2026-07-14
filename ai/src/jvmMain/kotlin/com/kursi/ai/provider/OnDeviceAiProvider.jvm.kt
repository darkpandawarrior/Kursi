package com.kursi.ai.provider

import com.siddharth.kmp.ai.UnavailableOnDeviceLlm

/**
 * Desktop/JVM on-device LLM tier (consolidation #7): routes through toolkit `:ai`'s
 * [UnavailableOnDeviceLlm] (no on-device model on desktop) instead of a hand-rolled duplicate.
 */
actual class OnDeviceAiProvider actual constructor() : AiProvider {
    override val id = "on_device"
    override val displayName = "On-device AI"

    override suspend fun isAvailable(): Boolean = UnavailableOnDeviceLlm.isAvailable()

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): String = UnavailableOnDeviceLlm.generate(messages.toOnDevicePrompt()) ?: ""
}
