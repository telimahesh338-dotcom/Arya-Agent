package com.arya.perception

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Fast, lightweight 64-bit Perceptual Difference Hash (dHash) engine.
 * Downsamples frames to 9x8 pixels to compute gradient differences.
 * Highly robust against minor compression artifacts, subpixel anti-aliasing, and cursor blinks.
 */
object VisualHasher {

    private const val HASH_WIDTH = 9
    private const val HASH_HEIGHT = 8

    /**
     * Computes a 64-bit dHash of the given bitmap.
     */
    fun computeDHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, HASH_WIDTH, HASH_HEIGHT, true)
        var hash = 0L

        try {
            val pixels = IntArray(HASH_WIDTH * HASH_HEIGHT)
            scaled.getPixels(pixels, 0, HASH_WIDTH, 0, 0, HASH_WIDTH, HASH_HEIGHT)

            // Convert to grayscale intensities
            val grays = IntArray(pixels.size)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                grays[i] = (r * 299 + g * 587 + b * 114) / 1000
            }

            // Compare adjacent pixels horizontally across 8 rows
            var bitIndex = 0
            for (y in 0 until HASH_HEIGHT) {
                val rowOffset = y * HASH_WIDTH
                for (x in 0 until (HASH_WIDTH - 1)) {
                    val left = grays[rowOffset + x]
                    val right = grays[rowOffset + x + 1]
                    if (left > right) {
                        hash = hash or (1L shl bitIndex)
                    }
                    bitIndex++
                }
            }
        } finally {
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }

        return hash
    }

    /**
     * Computes the Hamming distance (number of differing bits) between two 64-bit hashes.
     * Distance in range [0..64]:
     * - 0..3: Visually identical / negligible subpixel drift
     * - 4..10: Minor UI update (e.g. cursor blink, highlight, status bar icon)
     * - > 10: Meaningful visual screen change
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    /**
     * Returns true if two hashes represent visually equivalent screens within tolerance.
     */
    fun isVisuallySimilar(hash1: Long, hash2: Long, maxDistance: Int = 3): Boolean {
        return hammingDistance(hash1, hash2) <= maxDistance
    }
}
