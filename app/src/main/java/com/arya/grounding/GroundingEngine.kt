package com.arya.grounding

import com.arya.diagnostics.AryaLogger
import com.arya.perception.ScreenState
import com.arya.perception.UiNode
import com.arya.perception.ocr.OcrBlock

/**
 * Production-grade GUI Grounding Engine for ARYA.
 *
 * Enforces strict 6-layer priority:
 * 1. Direct Accessibility Node Match (ID / viewIdResourceName)
 * 2. Semantic UI Element (fuzzy match on class / type / structure)
 * 3. Text & Content Description Match (live accessibility tree)
 * 4. On-Device OCR Block Match
 * 5. Taproot Grounding Coordinate Snapping (smallest bounding container)
 * 6. Viewport Clamped Coordinate Fallback (last resort)
 */
class GroundingEngine {

    /**
     * Resolves an abstract action intention or predicted coordinate into a verified GroundedTarget.
     */
    fun groundTarget(
        screenState: ScreenState,
        targetNodeId: String? = null,
        targetText: String? = null,
        predictedX: Float? = null,
        predictedY: Float? = null,
        ocrBlocks: List<OcrBlock> = emptyList()
    ): GroundedTarget {
        // Priority 1: Direct Accessibility Node Match by ID
        if (!targetNodeId.isNullOrBlank()) {
            val node = screenState.findNodeById(targetNodeId)
            if (node != null && node.isVisibleToUser) {
                AryaLogger.d(TAG, "Grounded via Priority 1 (Accessibility Node ID): ${node.id}")
                return createFromUiNode(node, GroundingSource.ACCESSIBILITY_NODE, 1.0f)
            }
        }

        // Priority 2: Semantic Text / Content Description Match in UI Tree
        if (!targetText.isNullOrBlank()) {
            val matchingNodes = screenState.findNodesByText(targetText, ignoreCase = true)
            if (matchingNodes.isNotEmpty()) {
                // Prefer actionable nodes first, then smallest area (most specific target)
                val best = matchingNodes.minWithOrNull(
                    compareBy<UiNode> { !it.isActionable }
                        .thenBy { it.area }
                ) ?: matchingNodes.first()

                AryaLogger.d(TAG, "Grounded via Priority 2/3 (Text Match): '${best.semanticLabel}'")
                return createFromUiNode(best, GroundingSource.TEXT_DESCRIPTION, 0.95f, targetText = targetText)
            }

            // Priority 4: OCR Text Blocks
            val matchingOcr = ocrBlocks.filter { it.text.contains(targetText, ignoreCase = true) }
            if (matchingOcr.isNotEmpty()) {
                val bestOcr = matchingOcr.minByOrNull { it.area } ?: matchingOcr.first()
                AryaLogger.d(TAG, "Grounded via Priority 4 (OCR Match): '${bestOcr.text}'")
                return GroundedTarget(
                    stableId = bestOcr.id,
                    source = GroundingSource.OCR_BOUNDS,
                    confidence = 0.85f,
                    screenX = bestOcr.centerX,
                    screenY = bestOcr.centerY,
                    boundsLeft = bestOcr.boundsLeft,
                    boundsTop = bestOcr.boundsTop,
                    boundsRight = bestOcr.boundsRight,
                    boundsBottom = bestOcr.boundsBottom,
                    isActionable = true,
                    semanticLabel = bestOcr.text,
                    targetText = targetText
                )
            }
        }

        // Priority 5: Vision Grounding (Taproot Snapping)
        if (predictedX != null && predictedY != null) {
            val (snappedNode, snapMethod) = screenState.findGroundedNodeAt(predictedX, predictedY)
            if (snappedNode != null) {
                val confidence = if (snapMethod == "contained_smallest") 0.90f else 0.75f
                AryaLogger.d(TAG, "Grounded via Priority 5 (Taproot Snap: $snapMethod): ${snappedNode.semanticLabel}")
                return createFromUiNode(
                    node = snappedNode,
                    source = GroundingSource.VISION_SNAPPING,
                    confidence = confidence,
                    warning = "Snapped via $snapMethod"
                )
            }

            // Priority 6: Coordinate Fallback (Clamped to viewport bounds)
            val clampedX = predictedX.coerceIn(0f, screenState.screenWidth.toFloat())
            val clampedY = predictedY.coerceIn(0f, screenState.screenHeight.toFloat())

            AryaLogger.w(TAG, "Priority 6 Fallback: Using raw clamped coordinates ($clampedX, $clampedY)")
            return GroundedTarget(
                stableId = "fallback_${clampedX.toInt()}_${clampedY.toInt()}",
                source = GroundingSource.COORDINATE_FALLBACK,
                confidence = 0.40f,
                screenX = clampedX,
                screenY = clampedY,
                boundsLeft = (clampedX - 20).toInt().coerceAtLeast(0),
                boundsTop = (clampedY - 20).toInt().coerceAtLeast(0),
                boundsRight = (clampedX + 20).toInt().coerceAtMost(screenState.screenWidth),
                boundsBottom = (clampedY + 20).toInt().coerceAtMost(screenState.screenHeight),
                isActionable = false,
                semanticLabel = "Viewport Point ($clampedX, $clampedY)",
                warning = "Ungrounded model coordinate fallback"
            )
        }

        // Complete failure fallback (center of viewport)
        val midX = screenState.screenWidth / 2f
        val midY = screenState.screenHeight / 2f
        return GroundedTarget(
            stableId = "screen_center_fallback",
            source = GroundingSource.COORDINATE_FALLBACK,
            confidence = 0.10f,
            screenX = midX,
            screenY = midY,
            boundsLeft = 0,
            boundsTop = 0,
            boundsRight = screenState.screenWidth,
            boundsBottom = screenState.screenHeight,
            isActionable = false,
            semanticLabel = "Viewport Center Fallback",
            warning = "No viable target or coordinate provided"
        )
    }

    /**
     * Pre-action verification: checks if target is still valid in the current screen state.
     */
    fun isTargetValid(target: GroundedTarget, currentState: ScreenState): Boolean {
        if (target.nodeId != null) {
            val node = currentState.findNodeById(target.nodeId)
            return node != null && node.isVisibleToUser && node.isEnabled
        }

        if (target.targetText != null) {
            val nodes = currentState.findNodesByText(target.targetText)
            return nodes.any { it.isVisibleToUser && it.isEnabled }
        }

        // For coordinates, ensure within bounds
        return target.screenX in 0f..currentState.screenWidth.toFloat() &&
               target.screenY in 0f..currentState.screenHeight.toFloat()
    }

    private fun createFromUiNode(
        node: UiNode,
        source: GroundingSource,
        confidence: Float,
        targetText: String? = null,
        warning: String? = null
    ): GroundedTarget {
        return GroundedTarget(
            stableId = node.id,
            source = source,
            confidence = confidence,
            screenX = node.centerX,
            screenY = node.centerY,
            boundsLeft = node.boundsLeft,
            boundsTop = node.boundsTop,
            boundsRight = node.boundsRight,
            boundsBottom = node.boundsBottom,
            isActionable = node.isActionable,
            semanticLabel = node.semanticLabel,
            nodeId = node.id,
            targetText = targetText,
            warning = warning
        )
    }

    companion object {
        private const val TAG = "GroundingEngine"
    }
}
