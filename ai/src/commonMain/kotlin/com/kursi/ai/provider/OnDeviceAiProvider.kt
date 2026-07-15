package com.kursi.ai.provider

import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider

expect class OnDeviceAiProvider() : AiProvider

/**
 * Flattens a chat-shaped [AiMessage] list into the single text prompt toolkit `:ai`'s [com.siddharth.kmp.ai.OnDeviceLlm]
 * expects (that seam is one-shot text-in/text-out, not multi-turn) — shared by every platform actual
 * that routes through it.
 */
internal fun List<AiMessage>.toOnDevicePrompt(): String = joinToString("\n") { "${it.role}: ${it.content}" }
