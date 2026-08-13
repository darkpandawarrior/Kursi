package com.kursi.ai.provider

import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider

/**
 * Kotlin 2.4 requires an `expect class` to declare the abstract members it inherits, even when every
 * actual already implements them — previously the actuals alone satisfied the interface and this
 * line stood on its own. Without these the module does not compile, and the failure only surfaced
 * once the toolchain bump let dependency resolution get far enough to try.
 */
expect class OnDeviceAiProvider() : AiProvider {
    override val id: String

    override val displayName: String

    override suspend fun isAvailable(): Boolean

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): String
}

/**
 * Flattens a chat-shaped [AiMessage] list into the single text prompt toolkit `:ai`'s [com.siddharth.kmp.ai.OnDeviceLlm]
 * expects (that seam is one-shot text-in/text-out, not multi-turn) — shared by every platform actual
 * that routes through it.
 */
internal fun List<AiMessage>.toOnDevicePrompt(): String = joinToString("\n") { "${it.role}: ${it.content}" }
