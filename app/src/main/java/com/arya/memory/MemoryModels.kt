package com.arya.memory

import kotlinx.serialization.Serializable
import java.util.UUID

enum class MemoryType {
    WORKING,       // Transient task context (purged on task end)
    EPISODIC,      // Specific past task outcomes or events
    SEMANTIC,      // General factual knowledge established by user
    PEOPLE,        // Information about individuals (family, contacts, colleagues)
    APP,           // App-specific UI quirks, preferred configurations
    PREFERENCE     // Explicit user preferences ("Always use DuckDuckGo", "Prefer dark mode")
}

@Serializable
data class MemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val type: MemoryType,
    val content: String,
    var importance: Float, // 0.0 to 1.0 (>= 0.65 admitted into long-term)
    val confidence: Float = 1.0f,
    val source: String = "user_input",
    val userConfirmed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    var lastUsedAt: Long = System.currentTimeMillis(),
    var usageCount: Int = 0,
    val expiresAt: Long? = null,
    val decayRate: Float = 0.05f, // percentage decay per inactive week
    val tags: List<String> = emptyList()
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return expiresAt != null && now >= expiresAt
    }

    /**
     * Applies time-based importance decay. Items with low importance (< 0.25) become eviction candidates.
     */
    fun applyDecay(weeksPassed: Float) {
        if (!userConfirmed) {
            importance = (importance - (decayRate * weeksPassed)).coerceAtLeast(0.0f)
        }
    }

    fun markUsed(now: Long = System.currentTimeMillis()) {
        lastUsedAt = now
        usageCount++
        // Reinforce importance on repeated use
        importance = (importance + 0.05f).coerceAtMost(1.0f)
    }
}
