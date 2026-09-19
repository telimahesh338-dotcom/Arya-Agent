package com.arya.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

enum class ProviderType {
    GEMINI,
    ANTHROPIC,
    OPENAI,
    LOCAL
}

@Serializable
data class ApiKeyRecord(
    val keyId: String,
    val provider: ProviderType,
    val encryptedKey: String,
    val priority: Int = 0,
    val failureCount: Int = 0,
    val isRateLimited: Boolean = false,
    val rateLimitResetTimeMs: Long = 0L
)

/**
 * Secure hardware-backed storage for multi-provider API keys.
 * Implements round-robin rotation, failure tracking, and quota backoff.
 */
class KeyVault(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val keyPool = ConcurrentHashMap<ProviderType, MutableList<ApiKeyRecord>>()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadKeys()
    }

    private fun loadKeys() {
        ProviderType.entries.forEach { provider ->
            val serialized = securePrefs.getString(provider.name, null)
            if (!serialized.isNullOrBlank()) {
                try {
                    val list = json.decodeFromString<List<ApiKeyRecord>>(serialized)
                    keyPool[provider] = list.toMutableList()
                } catch (_: Throwable) {
                    keyPool[provider] = mutableListOf()
                }
            } else {
                keyPool[provider] = mutableListOf()
            }
        }
    }

    @Synchronized
    fun addKey(provider: ProviderType, keyId: String, rawApiKey: String, priority: Int = 0) {
        val list = keyPool.getOrPut(provider) { mutableListOf() }
        list.removeAll { it.keyId == keyId }
        list.add(
            ApiKeyRecord(
                keyId = keyId,
                provider = provider,
                encryptedKey = rawApiKey, // Stored inside hardware-encrypted SharedPreferences
                priority = priority
            )
        )
        persist(provider)
    }

    @Synchronized
    fun getActiveKey(provider: ProviderType): ApiKeyRecord? {
        val list = keyPool[provider] ?: return null
        val now = System.currentTimeMillis()

        // Filter keys that are not rate-limited, or whose rate limit has expired
        val available = list.map { record ->
            if (record.isRateLimited && now > record.rateLimitResetTimeMs) {
                record.copy(isRateLimited = false, rateLimitResetTimeMs = 0L)
            } else {
                record
            }
        }.filter { !it.isRateLimited }

        return available.maxByOrNull { it.priority - (it.failureCount * 2) }
    }

    @Synchronized
    fun reportFailure(provider: ProviderType, keyId: String, isRateLimit: Boolean = false) {
        val list = keyPool[provider] ?: return
        val index = list.indexOfFirst { it.keyId == keyId }
        if (index != -1) {
            val record = list[index]
            val updated = record.copy(
                failureCount = record.failureCount + 1,
                isRateLimited = isRateLimit,
                rateLimitResetTimeMs = if (isRateLimit) System.currentTimeMillis() + 60_000L else 0L
            )
            list[index] = updated
            persist(provider)
        }
    }

    @Synchronized
    fun reportSuccess(provider: ProviderType, keyId: String) {
        val list = keyPool[provider] ?: return
        val index = list.indexOfFirst { it.keyId == keyId }
        if (index != -1) {
            val record = list[index]
            if (record.failureCount > 0) {
                list[index] = record.copy(failureCount = 0)
                persist(provider)
            }
        }
    }

    private fun persist(provider: ProviderType) {
        val list = keyPool[provider] ?: return
        val serialized = json.encodeToString(list)
        securePrefs.edit().putString(provider.name, serialized).apply()
    }

    companion object {
        private const val PREFS_NAME = "arya_secure_vault"
    }
}
