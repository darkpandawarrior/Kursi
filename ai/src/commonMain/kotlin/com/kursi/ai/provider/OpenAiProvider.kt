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

private const val BASE_URL = "https://api.openai.com/v1/chat/completions"
private const val MODEL = "gpt-4o-mini"

class OpenAiProvider(
    private val apiKey: String,
) : AiProvider {
    override val id = "openai"
    override val displayName = "OpenAI (GPT-4o mini)"

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
        val openAiMessages =
            messages.map {
                OpenAiMessage(role = it.role.toOpenAiRole(), content = it.content)
            }
        return withTimeout(5_000) {
            runCatching {
                val response: OpenAiResponse =
                    client
                        .post(BASE_URL) {
                            header(HttpHeaders.Authorization, "Bearer $apiKey")
                            contentType(ContentType.Application.Json)
                            setBody(
                                OpenAiRequest(
                                    model = MODEL,
                                    messages = openAiMessages,
                                    maxTokens = config.maxTokens,
                                    temperature = config.temperature.toDouble(),
                                ),
                            )
                        }.body()
                response.choices
                    .firstOrNull()
                    ?.message
                    ?.content ?: ""
            }.getOrElse { "" }
        }
    }

    private fun AiMessage.Role.toOpenAiRole() =
        when (this) {
            AiMessage.Role.SYSTEM -> "system"
            AiMessage.Role.USER -> "user"
            AiMessage.Role.ASSISTANT -> "assistant"
        }
}

@Serializable
private data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double,
)

@Serializable
private data class OpenAiMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class OpenAiResponse(
    val choices: List<OpenAiChoice>,
)

@Serializable
private data class OpenAiChoice(
    val message: OpenAiMessage,
)
