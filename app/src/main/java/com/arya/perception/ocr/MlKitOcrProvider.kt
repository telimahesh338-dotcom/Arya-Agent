package com.arya.perception.ocr

import android.graphics.Bitmap
import com.arya.diagnostics.AryaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-Device OCR provider leveraging Google Play Services / ML Kit Text Recognition.
 * Operates offline, fast (~50-80ms), with zero token costs and zero network latency.
 * Falls back gracefully if ML Kit is unavailable on device.
 */
class MlKitOcrProvider : OCRProvider {

    override val name: String = "MLKit-OnDevice"

    override val isAvailable: Boolean
        get() = try {
            Class.forName("com.google.mlkit.vision.text.TextRecognition")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

    override suspend fun recognizeText(bitmap: Bitmap): List<OcrBlock> = withContext(Dispatchers.Default) {
        if (!isAvailable) {
            AryaLogger.w(TAG, "ML Kit Text Recognition is not installed or available on device.")
            return@withContext emptyList()
        }

        try {
            // Instantiate ML Kit via reflection to ensure optional dependency safety
            val recognizerClass = Class.forName("com.google.mlkit.vision.text.TextRecognition")
            val optionsClass = Class.forName("com.google.mlkit.vision.text.latin.TextRecognizerOptions")
            val defaultOptions = optionsClass.getField("DEFAULT_OPTIONS").get(null)
            val getClientMethod = recognizerClass.getMethod("getClient", optionsClass.interfaces[0])
            val client = getClientMethod.invoke(null, defaultOptions)

            val inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage")
            val fromBitmapMethod = inputImageClass.getMethod("fromBitmap", Bitmap::class.java, Int::class.javaPrimitiveType)
            val inputImage = fromBitmapMethod.invoke(null, bitmap, 0)

            val processMethod = client.javaClass.getMethod("process", inputImageClass)
            val task = processMethod.invoke(client, inputImage)

            val tasksClass = Class.forName("com.google.android.gms.tasks.Tasks")
            val awaitMethod = tasksClass.getMethod("await", task.javaClass.interfaces.firstOrNull() ?: task.javaClass)
            val result = awaitMethod.invoke(null, task)

            val textBlocksMethod = result.javaClass.getMethod("getTextBlocks")
            val rawBlocks = textBlocksMethod.invoke(result) as? List<*> ?: emptyList<Any>()

            val blocks = mutableListOf<OcrBlock>()
            for ((idx, raw) in rawBlocks.withIndex()) {
                if (raw == null) continue
                val text = raw.javaClass.getMethod("getText").invoke(raw) as? String ?: ""
                val box = raw.javaClass.getMethod("getBoundingBox").invoke(raw) as? android.graphics.Rect

                val boundsLeft = box?.left ?: 0
                val boundsTop = box?.top ?: 0
                val boundsRight = box?.right ?: 0
                val boundsBottom = box?.bottom ?: 0

                blocks.add(
                    OcrBlock(
                        id = "ocr_block_$idx",
                        text = text,
                        boundsLeft = boundsLeft,
                        boundsTop = boundsTop,
                        boundsRight = boundsRight,
                        boundsBottom = boundsBottom,
                        confidence = 0.95f
                    )
                )
            }

            AryaLogger.i(TAG, "ML Kit OCR recognized ${blocks.size} text blocks.")
            blocks
        } catch (t: Throwable) {
            AryaLogger.w(TAG, "ML Kit OCR processing error: ${t.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "MlKitOcrProvider"
    }
}
