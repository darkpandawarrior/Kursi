package com.kursi.ai.provider

fun buildProviderChain(config: AiProviderConfig): List<AiProvider> =
    buildList {
        if (config.useOnDevice) add(OnDeviceAiProvider())
        config.anthropicKey?.takeIf { it.isNotBlank() }?.let { add(AnthropicProvider(it)) }
        config.openAiKey?.takeIf { it.isNotBlank() }?.let { add(OpenAiProvider(it)) }
        config.geminiKey?.takeIf { it.isNotBlank() }?.let { add(GeminiProvider(it)) }
        add(IsmctsOnlyProvider())
    }

suspend fun firstAvailable(chain: List<AiProvider>): AiProvider = chain.firstOrNull { it.isAvailable() } ?: IsmctsOnlyProvider()
