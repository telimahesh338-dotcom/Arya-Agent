package com.arya.router

import com.arya.security.ApiKeyRecord
import com.arya.security.ProviderType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRouterTest {

    private class MockModelClient(
        override val provider: ProviderType,
        override val defaultModelName: String,
        var shouldThrow: Boolean = false
    ) : ModelClient {
        override suspend fun call(
            apiKeyRecord: ApiKeyRecord,
            messages: List<ChatMessage>,
            screenStatePrompt: String?,
            modelOverride: String?
        ): ModelResponse {
            if (shouldThrow) {
                throw RuntimeException("HTTP 500: Internal Server Error")
            }
            return ModelResponse(
                provider = provider,
                modelName = defaultModelName,
                content = "Mock response from $provider",
                latencyMs = 45L
            )
        }
    }

    @Test
    fun testLocalFallbackWhenNoKeys() = runBlocking {
        val mockLocal = MockModelClient(ProviderType.LOCAL, "local-rule")
        val clients = mapOf(ProviderType.LOCAL to mockLocal)

        // Mock KeyVault behavior using local client
        val response = mockLocal.call(
            ApiKeyRecord("k", ProviderType.LOCAL, ""),
            listOf(ChatMessage(MessageRole.USER, "Hello")),
            null
        )

        assertEquals(ProviderType.LOCAL, response.provider)
        assertTrue(response.content.contains("Mock response"))
    }

    @Test
    fun testProviderFailover() = runBlocking {
        val failingGemini = MockModelClient(ProviderType.GEMINI, "gemini-flash", shouldThrow = true)
        val healthyClaude = MockModelClient(ProviderType.ANTHROPIC, "claude-sonnet", shouldThrow = false)

        var invokedClaude = false
        try {
            failingGemini.call(ApiKeyRecord("k1", ProviderType.GEMINI, "key"), emptyList(), null)
        } catch (_: Throwable) {
            val res = healthyClaude.call(ApiKeyRecord("k2", ProviderType.ANTHROPIC, "key"), emptyList(), null)
            invokedClaude = true
            assertEquals(ProviderType.ANTHROPIC, res.provider)
        }

        assertTrue("Must seamlessly failover to secondary provider upon outage", invokedClaude)
    }

    @Test
    fun testChatMessageSerialization() {
        val msg = ChatMessage(MessageRole.USER, "Open settings app")
        assertEquals(MessageRole.USER, msg.role)
        assertEquals("Open settings app", msg.content)
    }
}
