package com.arya.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.arya.actions.ScrollDirection
import com.arya.diagnostics.AryaLogger
import com.arya.perception.AccessibilityReader
import com.arya.perception.ScreenState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Core Accessibility Service for ARYA.
 * Grants hands and eyes on device: captures interactive node hierarchy, resolves stable
 * semantic identifiers, executes direct node actions (semantic targeting), and dispatches
 * hardware-level touch gestures via suspending coroutines with timeout and cancellation safety.
 */
class AryaAccessibilityService : AccessibilityService() {

    private val reader = AccessibilityReader()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _activePackage = MutableStateFlow("")
    val activePackage: StateFlow<String> = _activePackage.asStateFlow()

    private val _activeActivity = MutableStateFlow<String?>(null)
    val activeActivity: StateFlow<String?> = _activeActivity.asStateFlow()

    private val _windowTitle = MutableStateFlow<String?>(null)
    val windowTitle: StateFlow<String?> = _windowTitle.asStateFlow()

    private val _currentScreenState = MutableStateFlow<ScreenState?>(null)
    val currentScreenState: StateFlow<ScreenState?> = _currentScreenState.asStateFlow()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        AryaLogger.i(TAG, "AryaAccessibilityService connected and ready.")
        AryaApplication.instance.capabilityManager.refreshCapabilities()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val pkg = event.packageName?.toString() ?: ""
            val cls = event.className?.toString() ?: ""

            if (pkg.isNotBlank() && pkg != packageName) {
                _activePackage.value = pkg
            }

            if (cls.isNotBlank()) {
                if (cls.endsWith("Activity") || cls.contains("Activity") || cls.contains("Screen")) {
                    _activeActivity.value = cls
                }
            }

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val title = event.text.firstOrNull()?.toString()
                    if (!title.isNullOrBlank()) {
                        _windowTitle.value = title
                    }
                    AryaLogger.d(TAG, "Window state changed: pkg=$pkg, cls=$cls, title=$title")
                }
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    AryaLogger.d(TAG, "Window hierarchy changed")
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    AryaLogger.d(TAG, "View focused: pkg=$pkg, cls=$cls")
                }
            }
        } catch (t: Throwable) {
            AryaLogger.w(TAG, "Error handling accessibility event: ${t.message}")
        }
    }

    override fun onInterrupt() {
        AryaLogger.w(TAG, "Accessibility service interrupted by OS or user.")
    }

    override fun onDestroy() {
        super.onDestroy()
        AryaLogger.i(TAG, "AryaAccessibilityService destroying.")
        _isConnected.value = false
        if (instance == this) {
            instance = null
        }
        AryaApplication.instance.capabilityManager.refreshCapabilities()
    }

    /**
     * Reads and normalizes the live Accessibility UI tree into an immutable ScreenState snapshot.
     */
    fun captureScreenState(): ScreenState {
        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            AryaLogger.w(TAG, "Failed to retrieve rootInActiveWindow: ${t.message}")
            null
        }

        val windowList = try {
            windows
        } catch (t: Throwable) {
            AryaLogger.w(TAG, "Failed to retrieve windows list: ${t.message}")
            null
        }

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val pkg = _activePackage.value.ifBlank {
            root?.packageName?.toString() ?: "unknown"
        }

        val state = reader.readScreenState(
            rootNode = root,
            activePackage = pkg,
            activityName = _activeActivity.value,
            windowTitle = _windowTitle.value,
            screenWidth = width,
            screenHeight = height,
            windows = windowList
        )

        _currentScreenState.value = state
        AryaLogger.i(
            TAG,
            "Captured ScreenState for '$pkg': ${state.actionableNodes.size} actionable nodes, hash=${state.structuralHash}"
        )
        return state
    }

    // =========================================================================
    // Semantic Node Actions (Target node directly without blind coordinates)
    // =========================================================================

    /**
     * Performs a click on a specific accessibility node identified by ID or semantic text.
     * If the node itself is not clickable, attempts to click the nearest clickable ancestor.
     * If native click fails, falls back to physical tap at node center bounds, or fallback coordinates.
     */
    suspend fun performClickOnNode(
        nodeId: String?,
        targetText: String? = null,
        fallbackX: Float? = null,
        fallbackY: Float? = null
    ): Boolean {
        AryaLogger.d(TAG, "performClickOnNode: id=$nodeId, text=$targetText, fallback=($fallbackX,$fallbackY)")
        val targetNode = findTargetNode(nodeId, targetText)

        if (targetNode != null) {
            try {
                // Find clickable target: targetNode or nearest clickable ancestor
                var clickableCandidate: AccessibilityNodeInfo? = targetNode
                while (clickableCandidate != null && !clickableCandidate.isClickable) {
                    val parent = try {
                        clickableCandidate.parent
                    } catch (_: Throwable) {
                        null
                    }
                    if (clickableCandidate !== targetNode) {
                        recycleSafely(clickableCandidate)
                    }
                    clickableCandidate = parent
                }

                if (clickableCandidate != null && clickableCandidate.isClickable) {
                    val clicked = clickableCandidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    AryaLogger.i(TAG, "Native ACTION_CLICK executed on candidate: success=$clicked")
                    if (clickableCandidate !== targetNode) {
                        recycleSafely(clickableCandidate)
                    }
                    if (clicked) {
                        recycleSafely(targetNode)
                        return true
                    }
                }

                // If native click action returned false or wasn't supported, fall back to center bounds
                val rect = Rect()
                targetNode.getBoundsInScreen(rect)
                val centerX = rect.exactCenterX()
                val centerY = rect.exactCenterY()
                recycleSafely(targetNode)

                if (rect.width() > 0 && rect.height() > 0) {
                    AryaLogger.i(TAG, "Native click failed; falling back to touch gesture at bounds center ($centerX, $centerY)")
                    return dispatchTap(centerX, centerY)
                }
            } catch (t: Throwable) {
                AryaLogger.w(TAG, "Exception during performClickOnNode: ${t.message}", t)
                recycleSafely(targetNode)
            }
        }

        // Fallback to screen coordinates if provided
        if (fallbackX != null && fallbackY != null && (fallbackX > 0f || fallbackY > 0f)) {
            AryaLogger.i(TAG, "Node not found or invalid; falling back to coordinate tap ($fallbackX, $fallbackY)")
            return dispatchTap(fallbackX, fallbackY)
        }

        AryaLogger.w(TAG, "performClickOnNode failed: no target node found and no valid coordinates.")
        return false
    }

    /**
     * Performs a long click on a target node or clickable ancestor, falling back to touch gesture.
     */
    suspend fun performLongClickOnNode(
        nodeId: String?,
        targetText: String? = null,
        fallbackX: Float? = null,
        fallbackY: Float? = null,
        durationMs: Long = 800L
    ): Boolean {
        AryaLogger.d(TAG, "performLongClickOnNode: id=$nodeId, text=$targetText")
        val targetNode = findTargetNode(nodeId, targetText)

        if (targetNode != null) {
            try {
                var candidate: AccessibilityNodeInfo? = targetNode
                while (candidate != null && !candidate.isLongClickable) {
                    val parent = try {
                        candidate.parent
                    } catch (_: Throwable) {
                        null
                    }
                    if (candidate !== targetNode) {
                        recycleSafely(candidate)
                    }
                    candidate = parent
                }

                if (candidate != null && candidate.isLongClickable) {
                    val clicked = candidate.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    AryaLogger.i(TAG, "Native ACTION_LONG_CLICK executed: success=$clicked")
                    if (candidate !== targetNode) {
                        recycleSafely(candidate)
                    }
                    if (clicked) {
                        recycleSafely(targetNode)
                        return true
                    }
                }

                val rect = Rect()
                targetNode.getBoundsInScreen(rect)
                val centerX = rect.exactCenterX()
                val centerY = rect.exactCenterY()
                recycleSafely(targetNode)

                if (rect.width() > 0 && rect.height() > 0) {
                    AryaLogger.i(TAG, "Native long click failed; fallback to touch gesture at ($centerX, $centerY)")
                    return dispatchTap(centerX, centerY, durationMs)
                }
            } catch (t: Throwable) {
                AryaLogger.w(TAG, "Exception during performLongClickOnNode: ${t.message}", t)
                recycleSafely(targetNode)
            }
        }

        if (fallbackX != null && fallbackY != null && (fallbackX > 0f || fallbackY > 0f)) {
            AryaLogger.i(TAG, "Fallback to coordinate long tap ($fallbackX, $fallbackY)")
            return dispatchTap(fallbackX, fallbackY, durationMs)
        }

        return false
    }

    /**
     * Sets text on the target editable node directly using ACTION_SET_TEXT.
     * Automatically requests input focus if needed.
     */
    suspend fun performSetTextOnNode(
        text: String,
        nodeId: String? = null,
        targetText: String? = null,
        fallbackX: Float? = null,
        fallbackY: Float? = null
    ): Boolean {
        AryaLogger.d(TAG, "performSetTextOnNode: text='$text', id=$nodeId")
        val targetNode = findTargetNode(nodeId, targetText)

        if (targetNode != null) {
            try {
                if (!targetNode.isFocused) {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                }

                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val set = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                AryaLogger.i(TAG, "Native ACTION_SET_TEXT executed: success=$set")
                recycleSafely(targetNode)
                if (set) return true
            } catch (t: Throwable) {
                AryaLogger.w(TAG, "Exception during performSetTextOnNode: ${t.message}", t)
                recycleSafely(targetNode)
            }
        }

        // If target node set text didn't succeed, fallback to tap coordinates + setFocusedText
        if (fallbackX != null && fallbackY != null) {
            AryaLogger.i(TAG, "Tapping field at ($fallbackX, $fallbackY) before setting text.")
            dispatchTap(fallbackX, fallbackY)
            kotlinx.coroutines.delay(250L)
        }

        val focusedSet = setFocusedText(text)
        AryaLogger.i(TAG, "Fallback setFocusedText result: $focusedSet")
        return focusedSet
    }

    /**
     * Clears text on the target editable node or currently focused node.
     */
    suspend fun performClearTextOnNode(
        nodeId: String? = null,
        targetText: String? = null,
        fallbackX: Float? = null,
        fallbackY: Float? = null
    ): Boolean {
        AryaLogger.d(TAG, "performClearTextOnNode: id=$nodeId, text=$targetText")
        return performSetTextOnNode("", nodeId, targetText, fallbackX, fallbackY)
    }

    /**
     * Dispatches a sustained drag gesture between screen coordinates.
     */
    suspend fun dispatchDrag(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 650L
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSuspending(gesture)
    }

    /**
     * Performs a scroll on the target scrollable node or active window.
     * Prefers native ACTION_SCROLL_FORWARD/BACKWARD, falling back to gesture swipe.
     */
    suspend fun performScrollOnNode(
        direction: ScrollDirection,
        nodeId: String? = null,
        distanceFraction: Float = 0.5f
    ): Boolean {
        AryaLogger.d(TAG, "performScrollOnNode: direction=$direction, nodeId=$nodeId")
        val targetNode = if (!nodeId.isNullOrBlank()) findNodeById(nodeId) else null

        if (targetNode != null && targetNode.isScrollable) {
            try {
                val scrollAction = when (direction) {
                    ScrollDirection.DOWN, ScrollDirection.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    ScrollDirection.UP, ScrollDirection.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                val ok = targetNode.performAction(scrollAction)
                AryaLogger.i(TAG, "Native scroll action ($scrollAction) result: $ok")
                if (ok) {
                    recycleSafely(targetNode)
                    return true
                }
            } catch (t: Throwable) {
                AryaLogger.w(TAG, "Exception during native scroll: ${t.message}", t)
            } finally {
                recycleSafely(targetNode)
            }
        }

        // Fallback to gesture swipe
        val displayMetrics = resources.displayMetrics
        val midX = displayMetrics.widthPixels / 2f
        val midY = displayMetrics.heightPixels / 2f
        val distance = displayMetrics.heightPixels * distanceFraction.coerceIn(0.1f, 0.9f) * 0.5f

        val (startY, endY) = when (direction) {
            ScrollDirection.DOWN -> (midY + distance) to (midY - distance)
            ScrollDirection.UP -> (midY - distance) to (midY + distance)
            ScrollDirection.LEFT, ScrollDirection.RIGHT -> midY to midY
        }

        val (startX, endX) = when (direction) {
            ScrollDirection.LEFT -> (midX + distance) to (midX - distance)
            ScrollDirection.RIGHT -> (midX - distance) to (midX + distance)
            else -> midX to midX
        }

        AryaLogger.i(TAG, "Fallback swipe scroll: ($startX, $startY) -> ($endX, $endY)")
        return dispatchSwipe(startX, startY, endX, endY, 350L)
    }

    // =========================================================================
    // Node Discovery Helpers
    // =========================================================================

    private fun findTargetNode(nodeId: String?, targetText: String?): AccessibilityNodeInfo? {
        if (!nodeId.isNullOrBlank()) {
            val byId = findNodeById(nodeId)
            if (byId != null) return byId
        }

        if (!targetText.isNullOrBlank()) {
            val byText = findNodesByText(targetText)
            if (byText.isNotEmpty()) {
                val best = byText.first()
                byText.drop(1).forEach { recycleSafely(it) }
                return best
            }
        }

        return null
    }

    fun findNodeById(nodeId: String): AccessibilityNodeInfo? {
        val root = try {
            rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return null

        // Try fast view id search if ID contains #resourceName
        val resName = if (nodeId.contains("#")) {
            nodeId.substringAfter("#").substringBefore("_").substringBefore("@")
        } else null

        if (!resName.isNullOrBlank()) {
            try {
                val matches = root.findAccessibilityNodeInfosByViewId(resName)
                if (!matches.isNullOrEmpty()) {
                    val candidate = matches.first()
                    matches.drop(1).forEach { recycleSafely(it) }
                    return candidate
                }
            } catch (_: Throwable) {
                // Ignore fast search errors
            }
        }

        // Full BFS search through live hierarchy
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: break
            try {
                val nodePkg = node.packageName?.toString() ?: ""
                val resId = node.viewIdResourceName
                val className = node.className?.toString() ?: ""
                val text = node.text?.toString()
                val desc = node.contentDescription?.toString()

                // Check direct matching against stable ID attributes
                if (resName != null && resId?.endsWith(resName) == true) {
                    clearQueue(queue)
                    return node
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(child)
                    }
                }
            } catch (_: Throwable) {
                // Ignore stale node during queue scan
            } finally {
                if (node !== root) {
                    // Do not recycle immediately if it's the target match
                }
            }
        }

        return null
    }

    fun findNodesByText(text: String): List<AccessibilityNodeInfo> {
        val root = try {
            rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return emptyList()

        return try {
            root.findAccessibilityNodeInfosByText(text) ?: emptyList()
        } catch (t: Throwable) {
            AryaLogger.w(TAG, "Error finding nodes by text '$text': ${t.message}")
            emptyList()
        }
    }

    private fun clearQueue(queue: java.util.ArrayDeque<AccessibilityNodeInfo>) {
        while (queue.isNotEmpty()) {
            val node = queue.poll()
            recycleSafely(node)
        }
    }

    private fun recycleSafely(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT < 34) {
            try {
                @Suppress("DEPRECATION")
                node.recycle()
            } catch (_: Throwable) {
                // Stale node ignore
            }
        }
    }

    // =========================================================================
    // Physical Touch Gestures (Suspending Coroutines with Timeout & Cancellation)
    // =========================================================================

    /**
     * Dispatches a tap gesture at real screen coordinates (x, y) asynchronously.
     * Suspends until Android confirms gesture completion or timeout fires.
     */
    suspend fun dispatchTap(x: Float, y: Float, durationMs: Long = 100L): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSuspending(gesture)
    }

    /**
     * Dispatches a swipe gesture between real screen coordinates.
     */
    suspend fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 350L
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSuspending(gesture)
    }

    /**
     * Sets text on the currently focused editable node directly using ACTION_SET_TEXT.
     */
    fun setFocusedText(text: String): Boolean {
        val root = try {
            rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return false

        val focused = try {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } catch (_: Throwable) {
            null
        } ?: return false

        return try {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } catch (t: Throwable) {
            AryaLogger.w(TAG, "Failed to set focused text: ${t.message}")
            false
        } finally {
            recycleSafely(focused)
        }
    }

    /**
     * Triggers standard Android global actions (Back, Home, Notifications, Recent Apps).
     */
    fun performGlobal(action: Int): Boolean {
        return try {
            val res = performGlobalAction(action)
            AryaLogger.i(TAG, "performGlobalAction($action) returned: $res")
            res
        } catch (t: Throwable) {
            AryaLogger.w(TAG, "Error performing global action $action: ${t.message}")
            false
        }
    }

    /**
     * Suspends coroutine while Android Accessibility framework executes gesture.
     * Enforces timeout and safe cancellation handling.
     */
    suspend fun dispatchGestureSuspending(
        gesture: GestureDescription,
        timeoutMs: Long = GESTURE_TIMEOUT_MS
    ): Boolean {
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        AryaLogger.d(TAG, "Gesture completed successfully.")
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        AryaLogger.w(TAG, "Gesture cancelled by system.")
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                }

                try {
                    val dispatched = dispatchGesture(gesture, callback, null)
                    if (!dispatched) {
                        AryaLogger.w(TAG, "dispatchGesture returned false immediately.")
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                } catch (t: Throwable) {
                    AryaLogger.w(TAG, "dispatchGesture threw exception: ${t.message}")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }

                continuation.invokeOnCancellation {
                    AryaLogger.w(TAG, "Gesture coroutine cancelled by caller.")
                }
            }
        }

        if (result == null) {
            AryaLogger.w(TAG, "Gesture execution timed out after ${timeoutMs}ms.")
            return false
        }
        return result
    }

    companion object {
        private const val TAG = "AryaAccessibility"
        private const val GESTURE_TIMEOUT_MS = 4000L

        @Volatile
        var instance: AryaAccessibilityService? = null
            private set
    }
}
