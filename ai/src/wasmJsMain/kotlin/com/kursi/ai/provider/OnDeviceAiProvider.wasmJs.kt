package com.kursi.ai.provider

actual class OnDeviceAiProvider actual constructor() : AiProvider {
    override val id = "on_device"
    override val displayName = "On-device AI"

    override suspend fun isAvailable() = false

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ) = ""
}
