package com.arya.actions

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import com.arya.android.AryaAccessibilityService
import kotlinx.coroutines.delay

/**
 * Low-level controller converting abstract ActionTypes into physical Accessibility gestures.
 */
class GestureController(private val context: Context) {

    suspend fun execute(action: ActionType): Boolean {
        val service = AryaAccessibilityService.instance ?: return false

        return when (action) {
            is ActionType.Tap -> {
                val ok = service.dispatchTap(action.x, action.y)
                delay(ACTION_SETTLE_MS)
                ok
            }
            is ActionType.LongTap -> {
                val ok = service.dispatchTap(action.x, action.y, action.durationMs)
                delay(ACTION_SETTLE_MS)
                ok
            }
            is ActionType.Swipe -> {
                val ok = service.dispatchSwipe(
                    action.startX,
                    action.startY,
                    action.endX,
                    action.endY,
                    action.durationMs
                )
                delay(ACTION_SETTLE_MS)
                ok
            }
            is ActionType.Scroll -> {
                // Approximate scroll gesture using viewport bounds
                val displayMetrics = context.resources.displayMetrics
                val midX = displayMetrics.widthPixels / 2f
                val midY = displayMetrics.heightPixels / 2f
                val distance = displayMetrics.heightPixels * action.distanceFraction * 0.5f

                val (startY, endY) = when (action.direction) {
                    ScrollDirection.DOWN -> (midY + distance) to (midY - distance)
                    ScrollDirection.UP -> (midY - distance) to (midY + distance)
                    ScrollDirection.LEFT -> midY to midY
                    ScrollDirection.RIGHT -> midY to midY
                }

                val (startX, endX) = when (action.direction) {
                    ScrollDirection.LEFT -> (midX + distance) to (midX - distance)
                    ScrollDirection.RIGHT -> (midX - distance) to (midX + distance)
                    else -> midX to midX
                }

                val ok = service.dispatchSwipe(startX, startY, endX, endY, 350L)
                delay(SCROLL_SETTLE_MS)
                ok
            }
            is ActionType.TypeText -> {
                if (action.targetX != null && action.targetY != null) {
                    service.dispatchTap(action.targetX, action.targetY)
                    delay(300L)
                }
                val ok = service.setFocusedText(action.text)
                delay(ACTION_SETTLE_MS)
                ok
            }
            is ActionType.PressBack -> {
                val ok = service.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
                delay(ACTION_SETTLE_MS)
                ok
            }
            is ActionType.PressHome -> {
                val ok = service.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
                delay(ACTION_SETTLE_MS)
                ok
            }
            is ActionType.LaunchApp -> {
                val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    delay(LAUNCH_SETTLE_MS)
                    true
                } else {
                    false
                }
            }
            is ActionType.Wait -> {
                delay((action.seconds * 1000).toLong())
                true
            }
            is ActionType.Confirm,
            is ActionType.TakeOver,
            is ActionType.Done,
            is ActionType.Fail -> {
                true
            }
        }
    }

    companion object {
        private const val ACTION_SETTLE_MS = 700L
        private const val SCROLL_SETTLE_MS = 900L
        private const val LAUNCH_SETTLE_MS = 1500L
    }
}
