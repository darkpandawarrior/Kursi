package com.kursi.ai.provider

enum class ProviderId { ON_DEVICE, ANTHROPIC, OPENAI, GEMINI, ISMCTS_ONLY }

data class AiProviderConfig(
    val selectedProvider: ProviderId = ProviderId.ISMCTS_ONLY,
    val anthropicKey: String? = null,
    val openAiKey: String? = null,
    val geminiKey: String? = null,
    val useOnDevice: Boolean = false,
)
