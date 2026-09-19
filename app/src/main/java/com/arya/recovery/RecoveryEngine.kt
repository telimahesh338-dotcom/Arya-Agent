package com.arya.recovery

import com.arya.actions.ActionType
import com.arya.diagnostics.AryaLogger
import com.arya.perception.ScreenState
import com.arya.reflection.RecommendedNextStep
import com.arya.reflection.ReflectionRecord
import kotlin.random.Random

/**
 * Self-Healing Recovery Engine for ARYA.
 *
 * Implements the sequence:
 * OBSERVE -> DIAGNOSE -> SAFE ALTERNATIVE -> VERIFY -> REFLECT -> REPLAN
 *
 * Handles dead taps, occlusions, popups, keyboards, crashes, and stuck loops.
 */
class RecoveryEngine(
    private val maxRetriesPerAction: Int = 3,
    private val maxBacksteps: Int = 2
) {

    private var retryCount = 0
    private var backstepCount = 0

    /**
     * Synthesizes a safe alternative RecoveryAction based on reflection diagnostics and current screen state.
     */
    fun planRecovery(
        reflection: ReflectionRecord,
        failedAction: ActionType,
        currentState: ScreenState,
        targetPackage: String? = null
    ): RecoveryAction {
        AryaLogger.i(TAG, "Formulating recovery for verdict=${reflection.verdict} (retries=$retryCount, backsteps=$backstepCount)")

        // 1. If loop detected or max retries exceeded -> escalate to Backstep or Human Intervention
        if (reflection.isLoopDetected || retryCount >= maxRetriesPerAction) {
            if (backstepCount < maxBacksteps) {
                backstepCount++
                retryCount = 0
                AryaLogger.w(TAG, "Executing backstep navigation to escape stuck loop ($backstepCount/$maxBacksteps).")
                return RecoveryAction(
                    type = RecoveryType.BACKSTEP_NAVIGATION,
                    actionToExecute = ActionType.PressBack,
                    explanation = "Navigating back to break stuck state loop.",
                    attemptNumber = backstepCount
                )
            } else {
                AryaLogger.e(TAG, "Recovery budgets exhausted. Requesting human intervention.")
                return RecoveryAction(
                    type = RecoveryType.REQUEST_HUMAN_INTERVENTION,
                    actionToExecute = ActionType.TakeOver(reason = "Unable to recover from stuck UI loop after $maxBacksteps backsteps."),
                    explanation = "Operator intervention requested due to unresponsive screen."
                )
            }
        }

        // 2. Handle based on recommended next step
        return when (reflection.nextStep) {
            RecommendedNextStep.RETRY_WITH_JITTER -> {
                retryCount++
                val jitteredAction = applyCoordinateJitter(failedAction)
                RecoveryAction(
                    type = RecoveryType.RETRY_WITH_COORDINATE_JITTER,
                    actionToExecute = jitteredAction,
                    explanation = "Retrying action with spatial jitter to clear dead edge boundary.",
                    attemptNumber = retryCount
                )
            }

            RecommendedNextStep.DISMISS_POPUP -> {
                retryCount++
                val dismissAction = findDismissalAction(currentState)
                RecoveryAction(
                    type = RecoveryType.DISMISS_MODAL_OR_PERMISSION,
                    actionToExecute = dismissAction,
                    explanation = "Dismissing blocking modal popup or permission banner.",
                    attemptNumber = retryCount
                )
            }

            RecommendedNextStep.HIDE_KEYBOARD -> {
                RecoveryAction(
                    type = RecoveryType.HIDE_KEYBOARD_BACK,
                    actionToExecute = ActionType.PressBack,
                    explanation = "Dismissing soft keyboard occluding target area."
                )
            }

            RecommendedNextStep.WAIT_FOR_LOAD -> {
                RecoveryAction(
                    type = RecoveryType.WAIT_FOR_NETWORK_OR_LOADING,
                    actionToExecute = ActionType.Wait(seconds = 2.5),
                    explanation = "Waiting for network loading or background render to settle."
                )
            }

            RecommendedNextStep.REPLAN -> {
                // If app crashed, relaunch it
                if (targetPackage != null && currentState.packageName != targetPackage) {
                    RecoveryAction(
                        type = RecoveryType.RELAUNCH_CRASHED_APP,
                        actionToExecute = ActionType.LaunchApp(packageName = targetPackage),
                        explanation = "Relaunching crashed target app ($targetPackage)."
                    )
                } else {
                    RecoveryAction(
                        type = RecoveryType.BACKSTEP_NAVIGATION,
                        actionToExecute = ActionType.PressBack,
                        explanation = "Backtracking to recover from unexpected state."
                    )
                }
            }

            RecommendedNextStep.RECOVER_BACKSTEP -> {
                backstepCount++
                RecoveryAction(
                    type = RecoveryType.BACKSTEP_NAVIGATION,
                    actionToExecute = ActionType.PressBack,
                    explanation = "Executing backstep to return to stable previous view."
                )
            }

            RecommendedNextStep.ABORT -> {
                RecoveryAction(
                    type = RecoveryType.ABORT_TASK,
                    actionToExecute = ActionType.Fail(reason = reflection.rationale),
                    explanation = "Aborting task due to irrecoverable failure: ${reflection.rationale}"
                )
            }

            RecommendedNextStep.PROCEED -> {
                RecoveryAction(
                    type = RecoveryType.RETRY_WITH_COORDINATE_JITTER,
                    actionToExecute = failedAction,
                    explanation = "Re-executing action."
                )
            }
        }
    }

    /**
     * Applies small random spatial jitter (+-15px) to coordinates to escape border/dead-pixel areas.
     */
    private fun applyCoordinateJitter(action: ActionType): ActionType {
        val jitterX = Random.nextInt(-15, 16).toFloat()
        val jitterY = Random.nextInt(-15, 16).toFloat()

        return when (action) {
            is ActionType.Tap -> action.copy(
                x = (action.x + jitterX).coerceAtLeast(0f),
                y = (action.y + jitterY).coerceAtLeast(0f)
            )
            is ActionType.LongTap -> action.copy(
                x = (action.x + jitterX).coerceAtLeast(0f),
                y = (action.y + jitterY).coerceAtLeast(0f)
            )
            else -> action
        }
    }

    /**
     * Locates a dismiss, cancel, close, or permission confirmation button on the dialog.
     */
    private fun findDismissalAction(state: ScreenState): ActionType {
        val dismissLabels = listOf("allow", "while using the app", "only this time", "close", "dismiss", "cancel", "not now", "ok", "got it")
        for (label in dismissLabels) {
            val matching = state.findNodesByText(label)
            val actionable = matching.firstOrNull { it.isClickable || it.isEnabled }
            if (actionable != null) {
                return ActionType.Tap(
                    nodeId = actionable.id,
                    targetText = actionable.text,
                    x = actionable.centerX,
                    y = actionable.centerY,
                    label = "Dismiss: ${actionable.semanticLabel}"
                )
            }
        }

        // If no explicit dismiss button found, attempt back press
        return ActionType.PressBack
    }

    /**
     * Resets recovery counters upon successful progress.
     */
    fun reset() {
        retryCount = 0
        backstepCount = 0
    }

    companion object {
        private const val TAG = "RecoveryEngine"
    }
}
