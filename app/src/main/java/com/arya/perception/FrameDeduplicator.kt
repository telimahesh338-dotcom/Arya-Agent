package com.arya.perception

import com.arya.diagnostics.AryaLogger

/**
 * Manages frame deduplication using perceptual visual hashes.
 * Discards redundant duplicate frames to prevent wasteful processing and token expense.
 */
class FrameDeduplicator(
    private val maxDistanceThreshold: Int = DEFAULT_THRESHOLD,
    private val maxHistorySize: Int = 10
) {

    private val hashHistory = ArrayDeque<Long>()

    /**
     * Checks if the given hash is a duplicate of the most recent frame.
     */
    fun isDuplicate(currentHash: Long): Boolean {
        val lastHash = hashHistory.lastOrNull() ?: return false
        val distance = VisualHasher.hammingDistance(lastHash, currentHash)
        val duplicate = distance <= maxDistanceThreshold
        if (duplicate) {
            AryaLogger.d(TAG, "Duplicate frame detected (hamming distance=$distance <= threshold=$maxDistanceThreshold)")
        }
        return duplicate
    }

    /**
     * Records a new frame hash into history.
     */
    fun recordFrame(hash: Long) {
        if (hashHistory.size >= maxHistorySize) {
            hashHistory.removeFirst()
        }
        hashHistory.addLast(hash)
    }

    /**
     * Resets history (e.g. after a deliberate action is dispatched).
     */
    fun reset() {
        hashHistory.clear()
    }

    val historySize: Int
        get() = hashHistory.size

    val lastRecordedHash: Long?
        get() = hashHistory.lastOrNull()

    companion object {
        private const val TAG = "FrameDeduplicator"
        const val DEFAULT_THRESHOLD = 3
    }
}
