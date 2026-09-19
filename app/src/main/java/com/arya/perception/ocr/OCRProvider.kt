package com.arya.perception.ocr

import android.graphics.Bitmap
import kotlinx.serialization.Serializable

/**
 * Normalized textual block extracted by an OCR engine.
 */
@Serializable
data class OcrBlock(
    val id: String,
    val text: String,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
    val confidence: Float = 1.0f,
    val lines: List<String> = emptyList()
) {
    val width: Int
        get() = maxOf(0, boundsRight - boundsLeft)

    val height: Int
        get() = maxOf(0, boundsBottom - boundsTop)

    val area: Int
        get() = width * height

    val centerX: Float
        get() = boundsLeft + (width / 2.0f)

    val centerY: Float
        get() = boundsTop + (height / 2.0f)

    fun contains(x: Float, y: Float): Boolean {
        return x >= boundsLeft && x <= boundsRight && y >= boundsTop && y <= boundsBottom
    }
}

/**
 * Replaceable abstraction interface for On-Device and Cloud OCR engines.
 */
interface OCRProvider {
    val name: String
    val isAvailable: Boolean

    suspend fun recognizeText(bitmap: Bitmap): List<OcrBlock>
}
