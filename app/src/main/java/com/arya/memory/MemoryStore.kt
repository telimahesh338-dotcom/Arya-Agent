package com.arya.memory

import com.arya.diagnostics.AryaLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe multi-tier Memory Store for ARYA.
 * Encapsulates WorkingMemory (task-scoped) and LongTermMemory (Episodic, People, Preference, Semantic).
 */
class MemoryStore(
    private val memoryGate: MemoryGate = MemoryGate()
) {

    private val mutex = Mutex()
    private val workingMemory = mutableListOf<MemoryItem>()
    private val longTermMemory = mutableListOf<MemoryItem>()

    /**
     * Submits a candidate statement through MemoryGate and stores it if admitted.
     */
    suspend fun processAndStore(statement: String, source: String = "user_input"): MemoryItem? = mutex.withLock {
        val decision = memoryGate.evaluate(statement, source)

        if (!decision.shouldStore) {
            // Store temporarily in working memory if non-sensitive
            if (decision.sanitizedContent != "[REDACTED]") {
                val workingItem = MemoryItem(
                    type = MemoryType.WORKING,
                    content = decision.sanitizedContent,
                    importance = decision.importanceScore,
                    source = source
                )
                workingMemory.add(workingItem)
            }
            return@withLock null
        }

        val item = MemoryItem(
            type = decision.type,
            content = decision.sanitizedContent,
            importance = decision.importanceScore,
            source = source,
            expiresAt = decision.expirationMs
        )

        longTermMemory.add(item)
        AryaLogger.i(TAG, "Stored in Long-Term Memory (${decision.type}): '${item.content}' (importance=${item.importance})")
        item
    }

    /**
     * Retrieves relevant memories matching a query string.
     */
    suspend fun query(
        queryText: String,
        typeFilter: MemoryType? = null,
        limit: Int = 5
    ): List<MemoryItem> = mutex.withLock {
        val now = System.currentTimeMillis()
        val lowerQuery = queryText.lowercase()

        val candidates = longTermMemory.filter { item ->
            !item.isExpired(now) &&
            (typeFilter == null || item.type == typeFilter) &&
            (item.content.lowercase().contains(lowerQuery) || lowerQuery.split(" ").any { kw -> kw.length > 3 && item.content.lowercase().contains(kw) })
        }

        val results = candidates
            .sortedByDescending { it.importance }
            .take(limit)

        results.forEach { it.markUsed(now) }
        results
    }

    /**
     * Explicit deletion of a specific memory item.
     */
    suspend fun forget(id: String): Boolean = mutex.withLock {
        val removed = longTermMemory.removeAll { it.id == id } || workingMemory.removeAll { it.id == id }
        AryaLogger.i(TAG, "Forgotten memory id '$id': removed=$removed")
        removed
    }

    /**
     * Purges all memories belonging to a specific type.
     */
    suspend fun clearByType(type: MemoryType): Int = mutex.withLock {
        val countBefore = longTermMemory.size
        longTermMemory.removeAll { it.type == type }
        val removed = countBefore - longTermMemory.size
        AryaLogger.i(TAG, "Cleared $removed memories of type $type")
        removed
    }

    /**
     * Purges transient working memory.
     */
    suspend fun clearWorkingMemory() = mutex.withLock {
        workingMemory.clear()
        AryaLogger.d(TAG, "Working memory cleared.")
    }

    /**
     * Runs periodic decay and evicts expired or sub-threshold items.
     */
    suspend fun decayAndEvict(weeksPassed: Float = 1.0f): Int = mutex.withLock {
        val now = System.currentTimeMillis()
        val beforeCount = longTermMemory.size

        longTermMemory.forEach { it.applyDecay(weeksPassed) }
        longTermMemory.removeAll { it.isExpired(now) || it.importance < MIN_IMPORTANCE_EVICTION }

        val evicted = beforeCount - longTermMemory.size
        if (evicted > 0) {
            AryaLogger.i(TAG, "Memory maintenance: evicted $evicted expired/decayed items.")
        }
        evicted
    }

    suspend fun getAllLongTerm(): List<MemoryItem> = mutex.withLock {
        longTermMemory.toList()
    }

    companion object {
        private const val TAG = "MemoryStore"
        private const val MIN_IMPORTANCE_EVICTION = 0.20f
    }
}
