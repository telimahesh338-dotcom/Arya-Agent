package com.arya.router

import com.arya.diagnostics.AryaLogger
import com.arya.security.ApiKeyRecord
import com.arya.security.KeyVault
import com.arya.security.ProviderType
import java.util.concurrent.ConcurrentHashMap

/**
 * Resilient, provider-independent Multi-Model Router for ARYA.
 *
 * Capabilities:
 * - Dynamic priority routing across Gemini, Anthropic, OpenAI, and Local engines.
 * - Hardware-backed key pool rotation via KeyVault.
 * - HTTP 429 quota backoff and automatic key switching.
 * - Circuit breaker: temporarily isolates failing providers (120s cool-down).
 * - Seamless sub-300ms failover across secondary providers during outages.
 */
class ModelRouter(
    private val keyVault: KeyVault,
    private val clients: Map<ProviderType, ModelClient> = mapOf(
        ProviderType.GEMINI to GeminiModelClient(),
        ProviderType.ANTHROPIC to AnthropicModelClient(),
        ProviderType.OPENAI to OpenAIModelClient(),
        ProviderType.LOCAL to LocalFallbackModelClient()
    ),
    val providerPriority: List<ProviderType> = listOf(
        ProviderType.GEMINI,
        ProviderType.ANTHROPIC,
        ProviderType.OPENAI,
        ProviderType.LOCAL
    )
) {

    private val circuitBreakerMap = ConcurrentHashMap<ProviderType, Long>()

    suspend fun route(
        messages: List<ChatMessage>,
        screenStatePrompt: String? = null,
        preferredProvider: ProviderType? = null
    ): ModelResponse {
        val now = System.currentTimeMillis()

        // Build prioritized candidate list
        val candidates = mutableListOf<ProviderType>()
        if (preferredProvider != null && isProviderAvailable(preferredProvider, now)) {
            candidates.add(preferredProvider)
        }
        providerPriority.forEach { p ->
            if (!candidates.contains(p) && isProviderAvailable(p, now)) {
                candidates.add(p)
            }
        }
        if (!candidates.contains(ProviderType.LOCAL)) {
            candidates.add(ProviderType.LOCAL)
        }

        var lastError: Throwable? = null

        for (provider in candidates) {
            val client = clients[provider] ?: continue

            // For LOCAL provider, API key is not required
            val activeKey = if (provider == ProviderType.LOCAL) {
                ApiKeyRecord(keyId = "local_mock", provider = ProviderType.LOCAL, encryptedKey = "")
            } else {
                keyVault.getActiveKey(provider)
            }

            if (activeKey == null) {
                AryaLogger.d(TAG, "Skipping $provider: No valid active API key found in vault.")
                continue
            }

            try {
                AryaLogger.i(TAG, "Routing inference request to provider: $provider")
                val response = client.call(activeKey, messages, screenStatePrompt)
                keyVault.reportSuccess(provider, activeKey.keyId)
                return response
            } catch (t: Throwable) {
                lastError = t
                val msg = t.message ?: ""
                val is429 = msg.contains("429") || msg.contains("quota") || msg.contains("rate limit")

                AryaLogger.w(TAG, "Call to provider $provider failed: $msg (is429=$is429). Rotating...")
                keyVault.reportFailure(provider, activeKey.keyId, isRateLimit = is429)

                // Trip circuit breaker on persistent failures
                tripCircuitBreaker(provider, now)
            }
        }

        // Final fallback to Local Engine
        val localClient = clients[ProviderType.LOCAL] ?: LocalFallbackModelClient()
        AryaLogger.w(TAG, "All cloud AI providers exhausted or offline. Falling back to local offline engine.")
        return localClient.call(
            ApiKeyRecord("local_fallback", ProviderType.LOCAL, ""),
            messages,
            screenStatePrompt
        )
    }

    private fun isProviderAvailable(provider: ProviderType, now: Long): Boolean {
        val tripUntil = circuitBreakerMap[provider] ?: 0L
        if (now < tripUntil) {
            return false
        }
        return true
    }

    private fun tripCircuitBreaker(provider: ProviderType, now: Long) {
        if (provider != ProviderType.LOCAL) {
            circuitBreakerMap[provider] = now + CIRCUIT_BREAKER_DURATION_MS
            AryaLogger.w(TAG, "Circuit breaker tripped for $provider for ${CIRCUIT_BREAKER_DURATION_MS / 1000}s")
        }
    }

    fun resetCircuitBreaker(provider: ProviderType) {
        circuitBreakerMap.remove(provider)
    }

    companion object {
        private const val TAG = "ModelRouter"
        private const val CIRCUIT_BREAKER_DURATION_MS = 120_000L // 2 minutes
    }
}
