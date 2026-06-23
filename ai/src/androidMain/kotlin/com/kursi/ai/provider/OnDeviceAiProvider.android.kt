package com.kursi.ai.provider

actual class OnDeviceAiProvider actual constructor() : AiProvider {

    override val id = "on_device"
    override val displayName = "On-device AI (Gemini Nano)"

    // Requires: com.google.ai.edge.aicore:aicore in androidMain dependencies.
    // Wire up GenerativeModel from the AICore SDK and replace this stub.
    override suspend fun isAvailable(): Boolean = false

    override suspend fun complete(messages: List<AiMessage>, config: AiConfig): String = ""
}
