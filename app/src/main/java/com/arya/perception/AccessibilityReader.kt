package com.arya.perception

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.arya.diagnostics.AryaLogger
import java.util.ArrayDeque

/**
 * Production-grade Accessibility Tree Reader.
 * Safely traverses the live accessibility hierarchy, handles detached/stale nodes,
 * filters occluded elements, and builds an immutable normalized ScreenState.
 */
class AccessibilityReader {

    private val tempRect = Rect()

    private val keyboardPackages = setOf(
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
        "com.sohu.inputmethod.sogou",
        "com.baidu.input",
        "android.inputmethodservice"
    )

    fun readScreenState(
        rootNode: AccessibilityNodeInfo?,
        activePackage: String,
        activityName: String? = null,
        windowTitle: String? = null,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        windows: List<AccessibilityWindowInfo>? = null
    ): ScreenState {
        if (rootNode == null) {
            return ScreenState(
                packageName = activePackage,
                activityName = activityName,
                windowTitle = windowTitle,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
        }

        val extractedNodes = mutableListOf<UiNode>()
        var isKeyboard = false
        var isDialog = false

        // Check window hierarchy if available
        windows?.forEach { win ->
            try {
                if (win.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    isKeyboard = true
                }
                if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION && win.isFocused && win.layer > 1) {
                    isDialog = true
                }
            } catch (_: Throwable) {
                // Ignore window interrogation failures
            }
        }

        val queue = ArrayDeque<NodeTraversalItem>()
        var counter = 0
        queue.add(NodeTraversalItem(rootNode, depth = 0, parentIndex = null))

        val visitedAddresses = HashSet<Int>()

        while (queue.isNotEmpty()) {
            val item = queue.poll() ?: break
            val node = item.node
            val depth = item.depth
            val parentIdx = item.parentIndex

            if (depth > MAX_TRAVERSAL_DEPTH) {
                recycleNode(node)
                continue
            }

            try {
                // Prevent infinite cycles in malformed vendor trees
                val identity = System.identityHashCode(node)
                if (visitedAddresses.contains(identity)) {
                    recycleNode(node)
                    continue
                }
                visitedAddresses.add(identity)

                val nodePkg = node.packageName?.toString() ?: activePackage
                if (keyboardPackages.contains(nodePkg.lowercase())) {
                    isKeyboard = true
                }

                node.getBoundsInScreen(tempRect)

                val left = tempRect.left
                val top = tempRect.top
                val right = tempRect.right
                val bottom = tempRect.bottom

                val isVisible = node.isVisibleToUser && right > left && bottom > top &&
                        right > 0 && bottom > 0 && left < screenWidth && top < screenHeight

                val className = node.className?.toString() ?: "android.view.View"
                val resId = node.viewIdResourceName
                val text = node.text?.toString()
                val desc = node.contentDescription?.toString()

                if (className.contains("Dialog") || className.contains("AlertDialog")) {
                    isDialog = true
                }

                val currentIndex = counter++
                val stableId = buildStableId(nodePkg, resId, className, text, desc, currentIndex)

                val uiNode = UiNode(
                    id = stableId,
                    nodeIndex = currentIndex,
                    packageName = nodePkg,
                    className = className,
                    resourceId = resId,
                    text = text,
                    contentDescription = desc,
                    boundsLeft = left,
                    boundsTop = top,
                    boundsRight = right,
                    boundsBottom = bottom,
                    isClickable = node.isClickable,
                    isLongClickable = node.isLongClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isEnabled = node.isEnabled,
                    isVisibleToUser = isVisible,
                    isFocused = node.isFocused,
                    isSelected = node.isSelected,
                    isCheckable = node.isCheckable,
                    isChecked = node.isChecked,
                    isPassword = node.isPassword,
                    depth = depth,
                    parentIndex = parentIdx
                )

                extractedNodes.add(uiNode)

                // Enqueue valid children
                val childCount = node.childCount
                for (i in 0 until childCount) {
                    try {
                        val child = node.getChild(i)
                        if (child != null) {
                            queue.add(
                                NodeTraversalItem(
                                    node = child,
                                    depth = depth + 1,
                                    parentIndex = currentIndex
                                )
                            )
                        }
                    } catch (e: Throwable) {
                        AryaLogger.w("AccessibilityReader", "Stale child node at index $i: ${e.message}")
                    }
                }
            } catch (e: Throwable) {
                AryaLogger.w("AccessibilityReader", "Error reading AccessibilityNodeInfo: ${e.message}")
            } finally {
                if (item.node !== rootNode) {
                    recycleNode(item.node)
                }
            }
        }

        return ScreenState(
            packageName = activePackage,
            activityName = activityName,
            windowTitle = windowTitle,
            timestamp = System.currentTimeMillis(),
            nodes = extractedNodes,
            isKeyboardVisible = isKeyboard,
            isDialogShowing = isDialog,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
    }

    private fun buildStableId(
        pkg: String,
        resId: String?,
        className: String,
        text: String?,
        desc: String?,
        index: Int
    ): String {
        val cleanRes = resId?.substringAfterLast("/")
        val cleanClass = className.substringAfterLast(".")
        val cleanLabel = (text ?: desc)?.take(12)?.replace(Regex("[^a-zA-Z0-9]"), "") ?: ""
        return "$pkg:$cleanClass${if (!cleanRes.isNullOrBlank()) "#$cleanRes" else ""}${if (cleanLabel.isNotBlank()) "_$cleanLabel" else ""}@$index"
    }

    private fun recycleNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        // On Android 14+ (API 34), node recycling is managed by the runtime and recycle() is a no-op
        if (Build.VERSION.SDK_INT < 34) {
            try {
                @Suppress("DEPRECATION")
                node.recycle()
            } catch (_: Throwable) {
                // Ignore recycled node exceptions
            }
        }
    }

    private data class NodeTraversalItem(
        val node: AccessibilityNodeInfo,
        val depth: Int,
        val parentIndex: Int?
    )

    companion object {
        private const val MAX_TRAVERSAL_DEPTH = 45
    }
}
