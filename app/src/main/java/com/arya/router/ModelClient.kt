package com.arya.router

import com.arya.diagnostics.AryaLogger
import com.arya.security.ApiKeyRecord
import com.arya.security.ProviderType
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

interface ModelClient {
    val provider: ProviderType
    val defaultModelName: String

    suspend fun call(
        apiKeyRecord: ApiKeyRecord,
        messages: List<ChatMessage>,
        screenStatePrompt: String? = null,
        modelOverride: String? = null
    ): ModelResponse
}

class GeminiModelClient(
    private val httpClient: HttpClient = HttpClient()
) : ModelClient {
    override val provider: ProviderType = ProviderType.GEMINI
    override val defaultModelName: String = "gemini-2.5-flash"

    override suspend fun call(
        apiKeyRecord: ApiKeyRecord,
        messages: List<ChatMessage>,
        screenStatePrompt: String?,
        modelOverride: String?
    ): ModelResponse = withContext(Dispatchers.IO) {
        val model = modelOverride ?: defaultModelName
        val start = System.currentTimeMillis()
        val apiKey = apiKeyRecord.encryptedKey

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        AryaLogger.d("GeminiClient", "Dispatching to Gemini ($model)")

        val combinedPrompt = buildString {
            if (!screenStatePrompt.isNullOrBlank()) {
                append("=== CURRENT SCREEN STATE ===\n")
                append(screenStatePrompt).append("\n\n")
            }
            messages.forEach { msg ->
                append("[${msg.role.name}]: ${msg.content}\n")
            }
        }

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", combinedPrompt)
                        })
                    }
                })
            }
        }.toString()

        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        val status = response.status.value
        val latency = System.currentTimeMillis() - start
        val rawText = response.bodyAsText()

        if (status in 200..299) {
            AryaLogger.i("GeminiClient", "Gemini response received in ${latency}ms")
            ModelResponse(
                provider = provider,
                modelName = model,
                content = rawText,
                latencyMs = latency
            )
        } else {
            AryaLogger.w("GeminiClient", "Gemini HTTP $status error: ${rawText.take(120)}")
            throw RuntimeException("Gemini HTTP $status: $rawText")
        }
    }
}

class OpenAIModelClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "https://api.openai.com/v1"
) : ModelClient {
    override val provider: ProviderType = ProviderType.OPENAI
    override val defaultModelName: String = "gpt-4o"

    override suspend fun call(
        apiKeyRecord: ApiKeyRecord,
        messages: List<ChatMessage>,
        screenStatePrompt: String?,
        modelOverride: String?
    ): ModelResponse = withContext(Dispatchers.IO) {
        val model = modelOverride ?: defaultModelName
        val start = System.currentTimeMillis()

        val endpoint = "$baseUrl/chat/completions"
        val apiKey = apiKeyRecord.encryptedKey

        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                if (!screenStatePrompt.isNullOrBlank()) {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", screenStatePrompt)
                    })
                }
                messages.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role.name.lowercase())
                        put("content", msg.content)
                    })
                }
            }
        }.toString()

        val response = httpClient.post(endpoint) {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        val status = response.status.value
        val latency = System.currentTimeMillis() - start
        val rawText = response.bodyAsText()

        if (status in 200..299) {
            AryaLogger.i("OpenAIClient", "OpenAI response received in ${latency}ms")
            ModelResponse(provider, model, rawText, latency)
        } else {
            throw RuntimeException("OpenAI HTTP $status: $rawText")
        }
    }
}

class AnthropicModelClient(
    private val httpClient: HttpClient = HttpClient()
) : ModelClient {
    override val provider: ProviderType = ProviderType.ANTHROPIC
    override val defaultModelName: String = "claude-3-7-sonnet-20250219"

    override suspend fun call(
        apiKeyRecord: ApiKeyRecord,
        messages: List<ChatMessage>,
        screenStatePrompt: String?,
        modelOverride: String?
    ): ModelResponse = withContext(Dispatchers.IO) {
        val model = modelOverride ?: defaultModelName
        val start = System.currentTimeMillis()
        val apiKey = apiKeyRecord.encryptedKey

        val endpoint = "https://api.anthropic.com/v1/messages"
        val requestBody = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            if (!screenStatePrompt.isNullOrBlank()) {
                put("system", screenStatePrompt)
            }
            putJsonArray("messages") {
                messages.filter { it.role != MessageRole.SYSTEM }.forEach { msg ->
                    add(buildJsonObject {
                        put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                        put("content", msg.content)
                    })
                }
            }
        }.toString()

        val response = httpClient.post(endpoint) {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        val status = response.status.value
        val latency = System.currentTimeMillis() - start
        val rawText = response.bodyAsText()

        if (status in 200..299) {
            AryaLogger.i("AnthropicClient", "Anthropic response received in ${latency}ms")
            ModelResponse(provider, model, rawText, latency)
        } else {
            throw RuntimeException("Anthropic HTTP $status: $rawText")
        }
    }
}

class LocalFallbackModelClient : ModelClient {
    override val provider: ProviderType = ProviderType.LOCAL
    override val defaultModelName: String = "on-device-rule-fallback"

    override suspend fun call(
        apiKeyRecord: ApiKeyRecord,
        messages: List<ChatMessage>,
        screenStatePrompt: String?,
        modelOverride: String?
    ): ModelResponse {
        val lastUserMessage = messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val content = "Action: Done(summary=\"Fallback rule responded to: $lastUserMessage\")"
        return ModelResponse(
            provider = provider,
            modelName = defaultModelName,
            content = content,
            latencyMs = 5L,
            isFallback = true
        )
    }
}
