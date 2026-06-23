package com.kursi.ai.provider

interface AiProvider {
    val id: String
    val displayName: String
    suspend fun complete(messages: List<AiMessage>, config: AiConfig = AiConfig()): String
    suspend fun isAvailable(): Boolean
}

data class AiMessage(val role: Role, val content: String) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

data class AiConfig(val maxTokens: Int = 256, val temperature: Float = 0.7f)
