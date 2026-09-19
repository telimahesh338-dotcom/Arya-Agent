package com.arya.router

import com.arya.security.ProviderType
import kotlinx.serialization.Serializable

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}

@Serializable
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val imageBase64: String? = null
)

@Serializable
data class ModelResponse(
    val provider: ProviderType,
    val modelName: String,
    val content: String,
    val latencyMs: Long,
    val tokenUsageEstimate: Int = 0,
    val isFallback: Boolean = false,
    val rawResponse: String? = null
)

enum class ProviderHealth {
    HEALTHY,
    DEGRADED,
    RATE_LIMITED,
    CIRCUIT_BROKEN
}
