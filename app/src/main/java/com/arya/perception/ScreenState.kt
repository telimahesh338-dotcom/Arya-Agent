package com.arya.perception

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * Complete normalized snapshot of the device screen at a single point in time.
 */
@Serializable
data class ScreenState(
    val packageName: String,
    val activityName: String? = null,
    val windowTitle: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val nodes: List<UiNode> = emptyList(),
    val isKeyboardVisible: Boolean = false,
    val isDialogShowing: Boolean = false,
    val isLoading: Boolean = false,
    val confidence: Float = 1.0f,
    val orientation: Int = 1,
    val screenWidth: Int = 1080,
    val screenHeight: Int = 2400,
    val screenshotMetadata: ScreenshotMetadata? = null,
    val visualHash: String? = null
) {
    val actionableNodes: List<UiNode> by lazy {
        nodes.filter { it.isActionable }
    }

    val clickableNodes: List<UiNode> by lazy {
        nodes.filter { (it.isClickable || it.isLongClickable) && it.isEnabled && it.isVisibleToUser }
    }

    val editableNodes: List<UiNode> by lazy {
        nodes.filter { it.isEditable && it.isEnabled && it.isVisibleToUser }
    }

    val scrollableNodes: List<UiNode> by lazy {
        nodes.filter { it.isScrollable && it.isEnabled && it.isVisibleToUser }
    }

    val focusedNode: UiNode? by lazy {
        nodes.firstOrNull { it.isFocused }
    }

    /**
     * Stable structural hash derived from actionable elements.
     * Changes whenever the layout, visible text, or interactive targets change.
     */
    val structuralHash: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = StringBuilder()
        buffer.append(packageName).append("|")
        buffer.append(isKeyboardVisible).append("|")
        buffer.append(isDialogShowing).append("|")

        actionableNodes.sortedBy { it.nodeIndex }.forEach { node ->
            buffer.append(node.className).append(":")
            buffer.append(node.resourceId ?: "").append(":")
            buffer.append(node.text ?: "").append(":")
            buffer.append(node.boundsLeft).append(",").append(node.boundsTop).append(";")
        }

        val hashBytes = digest.digest(buffer.toString().toByteArray())
        hashBytes.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a compact, token-efficient text representation with numbered legend badges.
     */
    fun toPromptRepresentation(maxElements: Int = 60): String {
        val sb = StringBuilder()
        sb.append("Package: ").append(packageName)
        if (!activityName.isNullOrBlank()) {
            sb.append(" | Activity: ").append(activityName)
        }
        if (isKeyboardVisible) sb.append(" [KEYBOARD ACTIVE]")
        if (isDialogShowing) sb.append(" [MODAL DIALOG]")
        sb.append("\nInteractive UI Elements:\n")

        val displayNodes = actionableNodes.take(maxElements)
        displayNodes.forEachIndexed { index, node ->
            sb.append(node.toCompactString(legendIndex = index + 1)).append("\n")
        }

        if (actionableNodes.size > maxElements) {
            sb.append("... and ").append(actionableNodes.size - maxElements).append(" more actionable elements.\n")
        }

        return sb.toString()
    }

    fun findNodeById(id: String): UiNode? {
        return nodes.firstOrNull { it.id == id }
    }

    /**
     * Finds nodes matching specific text or contentDescription substring.
     */
    fun findNodesByText(query: String, ignoreCase: Boolean = true): List<UiNode> {
        return nodes.filter { node ->
            val textMatches = node.text?.contains(query, ignoreCase = ignoreCase) == true
            val descMatches = node.contentDescription?.contains(query, ignoreCase = ignoreCase) == true
            textMatches || descMatches
        }
    }

    /**
     * Finds the first node matching specific text or contentDescription substring.
     */
    fun findFirstNodeByText(query: String, ignoreCase: Boolean = true): UiNode? {
        return findNodesByText(query, ignoreCase).firstOrNull()
    }

    /**
     * Finds the most specific (smallest area) actionable node containing coordinate (x, y).
     * If no node directly contains the point, finds the nearest clickable node within maxDistance.
     */
    fun findGroundedNodeAt(x: Float, y: Float, maxDistance: Float = 140f): Pair<UiNode?, String> {
        val containing = clickableNodes.filter { it.contains(x, y) }
        if (containing.isNotEmpty()) {
            val smallest = containing.minByOrNull { it.area }
            return Pair(smallest, "contained_smallest")
        }

        val nearby = clickableNodes.map { node ->
            val dx = node.centerX - x
            val dy = node.centerY - y
            val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            node to dist
        }.filter { it.second <= maxDistance }

        val closest = nearby.minByOrNull { it.second }
        if (closest != null) {
            return Pair(closest.first, "snapped_nearby (${closest.second.toInt()}px)")
        }

        return Pair(null, "raw_fallback")
    }
}
