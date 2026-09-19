package com.arya.perception.vision

import kotlinx.serialization.Serializable

enum class VisualElementType {
    BUTTON,
    ICON,
    INPUT_FIELD,
    IMAGE,
    DIALOG,
    TAB,
    FLOATING_ACTION_BUTTON,
    TEXT,
    LOADING_SPINNER,
    UNKNOWN
}

@Serializable
data class VisualElement(
    val id: String,
    val type: VisualElementType,
    val label: String? = null,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
    val confidence: Float = 1.0f
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

@Serializable
data class VisualAnalysisResult(
    val providerName: String,
    val summary: String,
    val elements: List<VisualElement> = emptyList(),
    val isModalDialogShowing: Boolean = false,
    val isLoadingSpinnerShowing: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
