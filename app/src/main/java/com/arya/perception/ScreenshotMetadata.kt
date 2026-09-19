package com.arya.perception

import kotlinx.serialization.Serializable

/**
 * Metadata accompanying a captured screen bitmap.
 * Encapsulates dimensions, scaling ratios, orientation, and perceptual visual hash.
 */
@Serializable
data class ScreenshotMetadata(
    val width: Int,
    val height: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val scaleX: Float,
    val scaleY: Float,
    val visualHash: Long = 0L,
    val orientation: Int = 1, // 1: Portrait, 2: Landscape
    val timestamp: Long = System.currentTimeMillis()
) {
    val visualHashHex: String
        get() = "%016x".format(visualHash)

    /**
     * Converts a capture-space coordinate to real device screen coordinate.
     */
    fun toScreenX(captureX: Float): Float = captureX * scaleX
    fun toScreenY(captureY: Float): Float = captureY * scaleY

    /**
     * Converts a real device screen coordinate to capture-space coordinate.
     */
    fun toCaptureX(screenX: Float): Float = if (scaleX > 0f) screenX / scaleX else screenX
    fun toCaptureY(screenY: Float): Float = if (scaleY > 0f) screenY / scaleY else screenY
}
