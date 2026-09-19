package com.arya.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualHasherTest {

    @Test
    fun testHammingDistanceIdentical() {
        val hash = 0x123456789abcdef0L
        assertEquals(0, VisualHasher.hammingDistance(hash, hash))
        assertTrue(VisualHasher.isVisuallySimilar(hash, hash, maxDistance = 3))
    }

    @Test
    fun testHammingDistanceSingleBitDiff() {
        val hash1 = 0x1L
        val hash2 = 0x3L // 1 bit diff
        assertEquals(1, VisualHasher.hammingDistance(hash1, hash2))
        assertTrue(VisualHasher.isVisuallySimilar(hash1, hash2, maxDistance = 3))
    }

    @Test
    fun testHammingDistanceDivergent() {
        val hash1 = 0x0000000000000000L
        val hash2 = -1L // all 64 bits inverted (0xFFFFFFFFFFFFFFFFL)
        assertEquals(64, VisualHasher.hammingDistance(hash1, hash2))
        assertFalse(VisualHasher.isVisuallySimilar(hash1, hash2, maxDistance = 3))
    }

    @Test
    fun testFrameDeduplicator() {
        val deduplicator = FrameDeduplicator(maxDistanceThreshold = 3, maxHistorySize = 5)

        val hashA = 0x1122334455667788L
        // First frame is not a duplicate
        assertFalse(deduplicator.isDuplicate(hashA))
        deduplicator.recordFrame(hashA)
        assertEquals(1, deduplicator.historySize)

        // Exact duplicate
        assertTrue(deduplicator.isDuplicate(hashA))

        // Minor drift (1 bit diff)
        val hashAMinor = hashA xor 0x1L
        assertTrue(deduplicator.isDuplicate(hashAMinor))

        // Significant change (10 bits diff)
        val hashB = hashA xor 0x3FFL
        assertFalse(deduplicator.isDuplicate(hashB))
        deduplicator.recordFrame(hashB)

        // Reset
        deduplicator.reset()
        assertEquals(0, deduplicator.historySize)
        assertFalse(deduplicator.isDuplicate(hashB))
    }

    @Test
    fun testScreenshotMetadataCoordinateConversion() {
        val metadata = ScreenshotMetadata(
            width = 540,
            height = 1200,
            originalWidth = 1080,
            originalHeight = 2400,
            scaleX = 2.0f,
            scaleY = 2.0f,
            visualHash = 0xABCDL
        )

        // Convert capture coordinate to screen coordinate
        assertEquals(200f, metadata.toScreenX(100f), 0.01f)
        assertEquals(500f, metadata.toScreenY(250f), 0.01f)

        // Convert screen coordinate to capture coordinate
        assertEquals(100f, metadata.toCaptureX(200f), 0.01f)
        assertEquals(250f, metadata.toCaptureY(500f), 0.01f)
    }
}
