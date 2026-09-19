package com.arya.perception.ocr

import android.graphics.Bitmap
import com.arya.diagnostics.AryaLogger

/**
 * Orchestrator for Optical Character Recognition across active providers.
 */
class OcrEngine(
    private val providers: List<OCRProvider> = listOf(MlKitOcrProvider())
) {

    suspend fun recognize(bitmap: Bitmap): List<OcrBlock> {
        for (provider in providers) {
            if (provider.isAvailable) {
                AryaLogger.d(TAG, "Attempting OCR recognition via provider: ${provider.name}")
                val results = provider.recognizeText(bitmap)
                if (results.isNotEmpty()) {
                    return results
                }
            }
        }

        AryaLogger.d(TAG, "No OCR provider produced text results or providers unavailable.")
        return emptyList()
    }

    /**
     * Searches recognized OCR blocks for query keywords.
     */
    fun findBlocksContaining(blocks: List<OcrBlock>, query: String, ignoreCase: Boolean = true): List<OcrBlock> {
        return blocks.filter { it.text.contains(query, ignoreCase = ignoreCase) }
    }

    companion object {
        private const val TAG = "OcrEngine"
    }
}
