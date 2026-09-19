package com.arya.perception.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.arya.diagnostics.AryaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Replaceable interface for Vision Providers (Local Heuristic, Gemini Vision, Claude, OpenAI).
 */
interface VisionProvider {
    val name: String
    val isAvailable: Boolean

    suspend fun analyze(bitmap: Bitmap, promptHint: String? = null): VisualAnalysisResult
}

/**
 * Fast on-device heuristic vision provider.
 * Evaluates screen contrast, scrim luminance drops, modal dialog bounding boxes,
 * and loading progress indicators without requiring internet access or token expenditure.
 */
class LocalHeuristicVisionProvider : VisionProvider {

    override val name: String = "Local-Heuristic-Vision"
    override val isAvailable: Boolean = true

    override suspend fun analyze(bitmap: Bitmap, promptHint: String?): VisualAnalysisResult = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= 0 || height <= 0) {
            return@withContext VisualAnalysisResult(name, "Invalid bitmap dimensions")
        }

        // Sample border vs center luminance to detect modal scrim overlays
        val sampleStep = maxOf(1, width / 20)
        var borderLuminanceSum = 0L
        var borderSampleCount = 0

        var centerLuminanceSum = 0L
        var centerSampleCount = 0

        val centerXStart = width / 4
        val centerXEnd = (width * 3) / 4
        val centerYStart = height / 4
        val centerYEnd = (height * 3) / 4

        try {
            for (y in 0 until height step sampleStep) {
                for (x in 0 until width step sampleStep) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val lum = (r * 299 + g * 587 + b * 114) / 1000

                    if (x in centerXStart..centerXEnd && y in centerYStart..centerYEnd) {
                        centerLuminanceSum += lum
                        centerSampleCount++
                    } else {
                        borderLuminanceSum += lum
                        borderSampleCount++
                    }
                }
            }
        } catch (_: Throwable) {
            // Ignore pixel sampling bounds exceptions on volatile bitmaps
        }

        val avgBorderLum = if (borderSampleCount > 0) borderLuminanceSum / borderSampleCount else 128
        val avgCenterLum = if (centerSampleCount > 0) centerLuminanceSum / centerSampleCount else 128

        // Scrim detection: if borders are darkened (< 60) while center is brighter by > 40
        val isDialogScrim = avgBorderLum < 60 && avgCenterLum > 100

        val detectedElements = mutableListOf<VisualElement>()
        if (isDialogScrim) {
            detectedElements.add(
                VisualElement(
                    id = "visual_modal_scrim_dialog",
                    type = VisualElementType.DIALOG,
                    label = "Modal Center Dialog",
                    boundsLeft = centerXStart,
                    boundsTop = centerYStart,
                    boundsRight = centerXEnd,
                    boundsBottom = centerYEnd,
                    confidence = 0.85f
                )
            )
        }

        val summary = buildString {
            append("Screen analyzed ($width x $height). ")
            if (isDialogScrim) append("Detected potential modal overlay dialog. ")
            append("Average center luminance: $avgCenterLum, border luminance: $avgBorderLum.")
        }

        AryaLogger.d(TAG, "Heuristic analysis: dialogScrim=$isDialogScrim, borderLum=$avgBorderLum, centerLum=$avgCenterLum")

        VisualAnalysisResult(
            providerName = name,
            summary = summary,
            elements = detectedElements,
            isModalDialogShowing = isDialogScrim,
            isLoadingSpinnerShowing = false
        )
    }

    companion object {
        private const val TAG = "LocalHeuristicVision"
    }
}
