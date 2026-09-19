package com.arya.perception.vision

import android.graphics.Bitmap
import com.arya.diagnostics.AryaLogger
import com.arya.perception.ScreenState
import com.arya.perception.ocr.OcrBlock
import com.arya.perception.ocr.OcrEngine

enum class PerceptionTier {
    ACCESSIBILITY_ONLY,
    OCR_AUGMENTED,
    FULL_VISION_AUGMENTED
}

data class FusedPerceptionResult(
    val tier: PerceptionTier,
    val screenState: ScreenState,
    val ocrBlocks: List<OcrBlock> = emptyList(),
    val visualAnalysis: VisualAnalysisResult? = null,
    val escalationReason: String? = null
)

/**
 * Orchestrator implementing ARYA's Tri-Tier Perception Escalation Strategy:
 * Tier 1: Accessibility Tree (fastest, ~15ms, zero token cost)
 * Tier 2: Local On-Device OCR (sub-60ms, zero token cost)
 * Tier 3: Visual Perception (~1200ms, reserved for canvas/icon-heavy apps)
 */
class VisionEngine(
    private val ocrEngine: OcrEngine = OcrEngine(),
    private val visionProviders: List<VisionProvider> = listOf(LocalHeuristicVisionProvider())
) {

    /**
     * Determines whether the current screen representation needs perceptual escalation,
     * and performs minimal necessary enhancement.
     */
    suspend fun processPerception(
        currentState: ScreenState,
        screenshot: Bitmap?,
        targetGoalHint: String? = null,
        forceOcr: Boolean = false,
        forceVision: Boolean = false
    ): FusedPerceptionResult {
        val hasAdequateNodes = currentState.actionableNodes.size >= 2

        // 1. Check if Accessibility alone is sufficient
        if (!forceOcr && !forceVision && hasAdequateNodes) {
            AryaLogger.d(TAG, "Tier 1: Accessibility hierarchy is sufficient (${currentState.actionableNodes.size} targets).")
            return FusedPerceptionResult(
                tier = PerceptionTier.ACCESSIBILITY_ONLY,
                screenState = currentState
            )
        }

        // If no screenshot is available, we cannot escalate to OCR or Vision
        if (screenshot == null) {
            AryaLogger.d(TAG, "No screenshot available; retaining Accessibility state.")
            return FusedPerceptionResult(
                tier = PerceptionTier.ACCESSIBILITY_ONLY,
                screenState = currentState,
                escalationReason = "Screenshot unavailable"
            )
        }

        // 2. Escalate to Tier 2: OCR
        AryaLogger.i(TAG, "Escalating to Tier 2: Running on-device OCR (nodes=${currentState.actionableNodes.size}).")
        val ocrBlocks = ocrEngine.recognize(screenshot)

        val ocrSufficient = ocrBlocks.isNotEmpty() && !forceVision
        if (ocrSufficient) {
            AryaLogger.i(TAG, "Tier 2 sufficient: Found ${ocrBlocks.size} OCR text blocks.")
            return FusedPerceptionResult(
                tier = PerceptionTier.OCR_AUGMENTED,
                screenState = currentState,
                ocrBlocks = ocrBlocks,
                escalationReason = "Accessibility insufficient; recovered text via OCR"
            )
        }

        // 3. Escalate to Tier 3: Visual Perception
        AryaLogger.i(TAG, "Escalating to Tier 3: Running visual analysis.")
        var visualResult: VisualAnalysisResult? = null
        for (provider in visionProviders) {
            if (provider.isAvailable) {
                visualResult = provider.analyze(screenshot, targetGoalHint)
                break
            }
        }

        return FusedPerceptionResult(
            tier = PerceptionTier.FULL_VISION_AUGMENTED,
            screenState = currentState,
            ocrBlocks = ocrBlocks,
            visualAnalysis = visualResult,
            escalationReason = "Visual inspection required for icon/canvas grounding"
        )
    }

    companion object {
        private const val TAG = "VisionEngine"
    }
}
