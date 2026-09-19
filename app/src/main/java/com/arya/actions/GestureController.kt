package com.arya.actions

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import com.arya.android.AryaAccessibilityService
import com.arya.diagnostics.AryaLogger
import kotlinx.coroutines.delay

/**
 * Low-level controller converting abstract ActionTypes into physical Accessibility gestures
 * and semantic UI tree interactions.
 *
 * CRITICAL POLICY: Prefers direct semantic node manipulation over blind screen coordinates.
 */
class GestureController(private val context: Context) {

    suspend fun execute(action: ActionType): Boolean {
        val service = AryaAccessibilityService.instance
        if (service == null) {
            AryaLogger.e(TAG, "Cannot execute action: AryaAccessibilityService is not connected.")
            return false
        }

        AryaLogger.i(TAG, "Executing action: ${action::class.simpleName}")

        return try {
            when (action) {
                is ActionType.Tap -> {
                    val ok = if (action.hasSemanticTarget) {
                        AryaLogger.d(TAG, "Executing semantic tap (nodeId=${action.nodeId}, targetText=${action.targetText})")
                        service.performClickOnNode(
                            nodeId = action.nodeId,
                            targetText = action.targetText,
                            fallbackX = if (action.x > 0f) action.x else null,
                            fallbackY = if (action.y > 0f) action.y else null
                        )
                    } else {
                        AryaLogger.d(TAG, "Executing coordinate tap at (${action.x}, ${action.y})")
                        service.dispatchTap(action.x, action.y)
                    }
                    delay(ACTION_SETTLE_MS)
                    ok
                }

                is ActionType.LongTap -> {
                    val ok = if (action.hasSemanticTarget) {
                        AryaLogger.d(TAG, "Executing semantic long tap (nodeId=${action.nodeId}, targetText=${action.targetText})")
                        service.performLongClickOnNode(
                            nodeId = action.nodeId,
                            targetText = action.targetText,
                            fallbackX = if (action.x > 0f) action.x else null,
                            fallbackY = if (action.y > 0f) action.y else null,
                            durationMs = action.durationMs
                        )
                    } else {
                        AryaLogger.d(TAG, "Executing coordinate long tap at (${action.x}, ${action.y}) for ${action.durationMs}ms")
                        service.dispatchTap(action.x, action.y, action.durationMs)
                    }
                    delay(ACTION_SETTLE_MS)
                    ok
                }

                is ActionType.Swipe -> {
                    AryaLogger.d(TAG, "Executing swipe: (${action.startX}, ${action.startY}) -> (${action.endX}, ${action.endY})")
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
                    AryaLogger.d(TAG, "Executing scroll: direction=${action.direction}, nodeId=${action.nodeId}")
                    val ok = service.performScrollOnNode(
                        direction = action.direction,
                        nodeId = action.nodeId,
                        distanceFraction = action.distanceFraction
                    )
                    delay(SCROLL_SETTLE_MS)
                    ok
                }

                is ActionType.TypeText -> {
                    val ok = if (action.hasSemanticTarget) {
                        AryaLogger.d(TAG, "Executing semantic set text '${action.text}' on nodeId=${action.nodeId}")
                        service.performSetTextOnNode(
                            text = action.text,
                            nodeId = action.nodeId,
                            targetText = action.targetText,
                            fallbackX = action.targetX,
                            fallbackY = action.targetY
                        )
                    } else {
                        if (action.targetX != null && action.targetY != null) {
                            AryaLogger.d(TAG, "Focusing input field at (${action.targetX}, ${action.targetY})")
                            service.dispatchTap(action.targetX, action.targetY)
                            delay(250L)
                        }
                        service.setFocusedText(action.text)
                    }
                    delay(ACTION_SETTLE_MS)
                    ok
                }

                is ActionType.PressBack -> {
                    AryaLogger.d(TAG, "Executing Global Back")
                    val ok = service.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
                    delay(ACTION_SETTLE_MS)
                    ok
                }

                is ActionType.PressHome -> {
                    AryaLogger.d(TAG, "Executing Global Home")
                    val ok = service.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
                    delay(ACTION_SETTLE_MS)
                    ok
                }

                is ActionType.LaunchApp -> {
                    AryaLogger.d(TAG, "Launching package: ${action.packageName}")
                    val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        delay(LAUNCH_SETTLE_MS)
                        true
                    } else {
                        AryaLogger.w(TAG, "Launch intent not found for package: ${action.packageName}")
                        false
                    }
                }

                is ActionType.Wait -> {
                    AryaLogger.d(TAG, "Waiting for ${action.seconds} seconds")
                    delay((action.seconds * 1000).toLong())
                    true
                }

                is ActionType.Drag -> {
                    AryaLogger.d(TAG, "Executing drag: (${action.startX}, ${action.startY}) -> (${action.endX}, ${action.endY})")
                    val ok = service.dispatchDrag(
                        action.startX,
                        action.startY,
                        action.endX,
                        action.endY,
                        action.durationMs
                    )
                    delay(ACTION_SETTLE_MS)
                    ok
                }

                is ActionType.ClearText -> {
                    AryaLogger.d(TAG, "Executing clear text on nodeId=${action.nodeId}, text=${action.targetText}")
                    val ok = service.performClearTextOnNode(
                        nodeId = action.nodeId,
                        targetText = action.targetText,
                        fallbackX = action.targetX,
                        fallbackY = action.targetY
                    )
                    delay(ACTION_SETTLE_MS)
                    ok
                }

                is ActionType.Retry -> {
                    AryaLogger.i(TAG, "Retrying action ${action.originalAction::class.simpleName} (attempt ${action.attemptNumber})")
                    delay(action.delayMs)
                    execute(action.originalAction)
                }

                is ActionType.Cancel -> {
                    AryaLogger.w(TAG, "Action cancelled: ${action.reason}")
                    false
                }

                is ActionType.Confirm,
                is ActionType.TakeOver,
                is ActionType.Done,
                is ActionType.Fail -> {
                    true
                }
            }
        } catch (t: Throwable) {
            AryaLogger.e(TAG, "Gesture execution failed with exception: ${t.message}", t)
            false
        }
    }

    companion object {
        private const val TAG = "GestureController"
        private const val ACTION_SETTLE_MS = 700L
        private const val SCROLL_SETTLE_MS = 900L
        private const val LAUNCH_SETTLE_MS = 1500L
    }
}
