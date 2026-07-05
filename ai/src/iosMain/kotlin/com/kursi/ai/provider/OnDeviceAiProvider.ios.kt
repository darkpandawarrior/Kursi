package com.kursi.ai.provider

actual class OnDeviceAiProvider actual constructor() : AiProvider {
    override val id = "on_device"
    override val displayName = "On-device AI (Apple Intelligence)"

    // FoundationModels (iOS 26+) requires a Swift bridge exposed via ObjC interop.
    // See docs/on_device_ios.md for the OnDeviceAiBridge.swift setup.
    override suspend fun isAvailable(): Boolean = false

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): String = ""
}
