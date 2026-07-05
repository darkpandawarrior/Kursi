package com.kursi.ai.provider

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://api.anthropic.com/v1/messages"
private const val MODEL = "claude-haiku-4-5-20251001"
private const val ANTHROPIC_VERSION = "2023-06-01"

class AnthropicProvider(
    private val apiKey: String,
) : AiProvider {
    override val id = "anthropic"
    override val displayName = "Anthropic (Claude Haiku)"

    private val client by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    override suspend fun isAvailable() = apiKey.isNotBlank()

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): String {
        val system = messages.firstOrNull { it.role == AiMessage.Role.SYSTEM }?.content ?: ""
        val userMessages =
            messages
                .filter { it.role != AiMessage.Role.SYSTEM }
                .map { AnthropicMessage(role = it.role.toAnthropicRole(), content = it.content) }

        return withTimeout(5_000) {
            runCatching {
                val response: AnthropicResponse =
                    client
                        .post(BASE_URL) {
                            header("x-api-key", apiKey)
                            header("anthropic-version", ANTHROPIC_VERSION)
                            contentType(ContentType.Application.Json)
                            setBody(
                                AnthropicRequest(
                                    model = MODEL,
                                    maxTokens = config.maxTokens,
                                    system = system.ifBlank { null },
                                    messages = userMessages,
                                ),
                            )
                        }.body()
                response.content.firstOrNull()?.text ?: ""
            }.getOrElse { "" }
        }
    }

    private fun AiMessage.Role.toAnthropicRole() =
        when (this) {
            AiMessage.Role.USER -> "user"
            AiMessage.Role.ASSISTANT -> "assistant"
            AiMessage.Role.SYSTEM -> "user"
        }
}

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String?,
    val messages: List<AnthropicMessage>,
)

@Serializable
private data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class AnthropicResponse(
    val content: List<AnthropicContent>,
)

@Serializable
private data class AnthropicContent(
    val text: String,
)
