package com.arya.grounding

import kotlinx.serialization.Serializable

enum class GroundingSource {
    ACCESSIBILITY_NODE,
    SEMANTIC_ELEMENT,
    TEXT_DESCRIPTION,
    OCR_BOUNDS,
    VISION_SNAPPING,
    COORDINATE_FALLBACK
}

/**
 * Normalized target resolved by the Grounding Engine.
 * Every target contains guaranteed spatial bounds, confidence, and provenance source.
 */
@Serializable
data class GroundedTarget(
    val stableId: String,
    val source: GroundingSource,
    val confidence: Float,
    val screenX: Float,
    val screenY: Float,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
    val isActionable: Boolean = true,
    val semanticLabel: String = "",
    val nodeId: String? = null,
    val targetText: String? = null,
    val warning: String? = null
) {
    val width: Int
        get() = maxOf(0, boundsRight - boundsLeft)

    val height: Int
        get() = maxOf(0, boundsBottom - boundsTop)

    val area: Int
        get() = width * height

    fun contains(x: Float, y: Float): Boolean {
        return x >= boundsLeft && x <= boundsRight && y >= boundsTop && y <= boundsBottom
    }
}
