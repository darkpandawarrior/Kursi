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

private const val BASE_URL =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent"

class GeminiProvider(private val apiKey: String) : AiProvider {

    override val id = "gemini"
    override val displayName = "Google Gemini (Flash Lite)"

    private val client by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    override suspend fun isAvailable() = apiKey.isNotBlank()

    override suspend fun complete(messages: List<AiMessage>, config: AiConfig): String {
        val systemInstruction = messages.firstOrNull { it.role == AiMessage.Role.SYSTEM }
            ?.let { GeminiContent(parts = listOf(GeminiPart(it.content))) }

        val contents = messages.filter { it.role != AiMessage.Role.SYSTEM }.map {
            GeminiContent(
                role = it.role.toGeminiRole(),
                parts = listOf(GeminiPart(it.content)),
            )
        }

        return withTimeout(5_000) {
            runCatching {
                val response: GeminiResponse = client.post(BASE_URL) {
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(
                        GeminiRequest(
                            contents = contents,
                            systemInstruction = systemInstruction,
                            generationConfig = GeminiGenerationConfig(
                                maxOutputTokens = config.maxTokens,
                                temperature = config.temperature.toDouble(),
                            ),
                        )
                    )
                }.body()
                response.candidates.firstOrNull()
                    ?.content?.parts?.firstOrNull()?.text ?: ""
            }.getOrElse { "" }
        }
    }

    private fun AiMessage.Role.toGeminiRole() = when (this) {
        AiMessage.Role.USER, AiMessage.Role.SYSTEM -> "user"
        AiMessage.Role.ASSISTANT -> "model"
    }
}

@Serializable
private data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
private data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(val text: String)

@Serializable
private data class GeminiGenerationConfig(
    @SerialName("maxOutputTokens") val maxOutputTokens: Int,
    val temperature: Double,
)

@Serializable
private data class GeminiResponse(val candidates: List<GeminiCandidate>)

@Serializable
private data class GeminiCandidate(val content: GeminiContent)
