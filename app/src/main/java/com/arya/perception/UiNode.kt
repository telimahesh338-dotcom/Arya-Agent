package com.arya.perception

import kotlinx.serialization.Serializable

/**
 * Immutable, normalized snapshot of an Accessibility UI element.
 * Completely detached from Android OS native pointers to avoid stale node exceptions.
 */
@Serializable
data class UiNode(
    val id: String,
    val nodeIndex: Int,
    val packageName: String,
    val className: String,
    val resourceId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val boundsLeft: Int = 0,
    val boundsTop: Int = 0,
    val boundsRight: Int = 0,
    val boundsBottom: Int = 0,
    val isClickable: Boolean = false,
    val isLongClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEnabled: Boolean = true,
    val isVisibleToUser: Boolean = true,
    val isFocused: Boolean = false,
    val isSelected: Boolean = false,
    val isCheckable: Boolean = false,
    val isChecked: Boolean = false,
    val isPassword: Boolean = false,
    val depth: Int = 0,
    val parentIndex: Int? = null,
    val childrenIndices: List<Int> = emptyList()
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

    val isActionable: Boolean
        get() = (isClickable || isLongClickable || isEditable || isScrollable) &&
                isEnabled && isVisibleToUser && area > 0

    val semanticLabel: String
        get() = when {
            !text.isNullOrBlank() -> text.trim()
            !contentDescription.isNullOrBlank() -> contentDescription.trim()
            !resourceId.isNullOrBlank() -> resourceId.substringAfterLast("/")
            else -> className.substringAfterLast(".")
        }

    fun contains(x: Float, y: Float): Boolean {
        return x >= boundsLeft && x <= boundsRight && y >= boundsTop && y <= boundsBottom
    }

    fun toCompactString(legendIndex: Int? = null): String {
        val type = className.substringAfterLast(".")
        val label = semanticLabel.take(35)
        val legendPrefix = if (legendIndex != null) "[$legendIndex] " else ""
        val clickableFlag = if (isClickable) " clickable" else ""
        val editableFlag = if (isEditable) " editable" else ""
        val scrollableFlag = if (isScrollable) " scrollable" else ""
        val focusFlag = if (isFocused) " focused" else ""

        return "$legendPrefix$type \"$label\" bounds=[$boundsLeft,$boundsTop][$boundsRight,$boundsBottom]$clickableFlag$editableFlag$scrollableFlag$focusFlag"
    }
}
