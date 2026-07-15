package com.kursi.ai.provider

import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class IsmctsOnlyProvider : AiProvider {
    override val id = "ismcts_only"
    override val displayName = "ISMCTS (offline)"

    override suspend fun isAvailable() = true

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): String {
        val userMessage = messages.lastOrNull { it.role == AiMessage.Role.USER }?.content ?: return ""
        return runCatching {
            val json = Json.parseToJsonElement(userMessage).jsonObject
            json["candidates"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("action")
                ?.jsonPrimitive
                ?.content ?: ""
        }.getOrElse { "" }
    }
}
