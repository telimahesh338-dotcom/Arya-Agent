package com.arya.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Core Accessibility Service for ARYA.
 * Grants hands and eyes on device: captures interactive node hierarchy and dispatches
 * hardware-level touch gestures via suspend functions.
 */
class AryaAccessibilityService : AccessibilityService() {

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _activePackage = MutableStateFlow("")
    val activePackage: StateFlow<String> = _activePackage.asStateFlow()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        AryaApplication.instance.capabilityManager.refreshCapabilities()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""
            if (pkg.isNotBlank() && pkg != packageName) {
                _activePackage.value = pkg
            }
        }
    }

    override fun onInterrupt() {
        // Accessibility feedback interrupted by user
    }

    override fun onDestroy() {
        super.onDestroy()
        _isConnected.value = false
        if (instance == this) {
            instance = null
        }
        AryaApplication.instance.capabilityManager.refreshCapabilities()
    }

    /**
     * Dispatches a tap gesture at real screen coordinates (x, y) asynchronously.
     * Suspends until Android confirms gesture completion.
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
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * Triggers standard Android global actions (Back, Home, Notifications, etc.)
     */
    fun performGlobal(action: Int): Boolean {
        return performGlobalAction(action)
    }

    private suspend fun dispatchGestureSuspending(gesture: GestureDescription): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
            val dispatched = dispatchGesture(gesture, callback, null)
            if (!dispatched && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    companion object {
        @Volatile
        var instance: AryaAccessibilityService? = null
            private set
    }
}
