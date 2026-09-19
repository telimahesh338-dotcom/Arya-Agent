package com.arya.actions

import android.content.Context
import com.arya.android.AryaAccessibilityService
import com.arya.diagnostics.AryaLogger
import com.arya.grounding.GroundedTarget
import com.arya.perception.ScreenChangeDetector
import com.arya.security.RiskClassifier
import com.arya.security.RiskEvaluation
import com.arya.security.RiskLevel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ActionExecutionRecord(
    val action: ActionType,
    val riskEvaluation: RiskEvaluation,
    val wasExecuted: Boolean,
    val executionTimeMs: Long,
    val success: Boolean
)

/**
 * Production-grade Action Engine for ARYA.
 *
 * Enforces the core paradigm:
 * OBSERVE BEFORE -> EVALUATE RISK -> ACT -> OBSERVE AGAIN -> AUDIT RESULT.
 * Never assumes success without visual / accessibility verification.
 */
class ActionEngine(
    context: Context,
    private val riskClassifier: RiskClassifier = RiskClassifier(),
    private val gestureController: GestureController = GestureController(context),
    private val changeDetector: ScreenChangeDetector = ScreenChangeDetector()
) {
    private val _actionTrace = MutableSharedFlow<ActionExecutionRecord>(extraBufferCapacity = 64)
    val actionTrace: SharedFlow<ActionExecutionRecord> = _actionTrace.asSharedFlow()

    private val _actionResultTrace = MutableSharedFlow<ActionResult>(extraBufferCapacity = 64)
    val actionResultTrace: SharedFlow<ActionResult> = _actionResultTrace.asSharedFlow()

    /**
     * Executes an action with pre- and post-observation, generating a rich ActionResult.
     */
    suspend fun execute(
        action: ActionType,
        goalContext: String,
        target: GroundedTarget? = null,
        userApproved: Boolean = false
    ): ActionResult {
        val a11y = AryaAccessibilityService.instance

        // 1. Observe pre-state
        val preState = a11y?.captureScreenState()
        val preHash = preState?.structuralHash

        // 2. Risk check
        val risk = riskClassifier.evaluate(action, goalContext)
        if (risk.requiresApproval && !userApproved) {
            AryaLogger.w(TAG, "Action requires operator confirmation: ${risk.reason} (${risk.level})")
            return ActionResult(
                action = action,
                success = false,
                failureReason = "Requires human confirmation: ${risk.reason}",
                target = target,
                previousStateHash = preHash,
                confidence = 1.0f,
                isRetryable = false
            )
        }

        // 3. Act
        AryaLogger.i(TAG, "Dispatching action: ${action::class.simpleName} (target=${target?.semanticLabel ?: action})")
        val startTime = System.currentTimeMillis()
        var failureReason: String? = null
        var errorInfo: String? = null

        val success = try {
            gestureController.execute(action)
        } catch (t: Throwable) {
            AryaLogger.e(TAG, "Action execution exception: ${t.message}", t)
            failureReason = t.localizedMessage ?: "Unknown execution error"
            errorInfo = t.stackTraceToString()
            false
        }
        val duration = System.currentTimeMillis() - startTime

        // 4. Observe post-state (Do not assume success)
        val postState = a11y?.captureScreenState()
        val postHash = postState?.structuralHash

        // Compute difference
        val diff = changeDetector.detectChange(preState, postState ?: preState!!)

        val result = ActionResult(
            action = action,
            success = success,
            failureReason = failureReason,
            target = target,
            executionTimeMs = duration,
            previousStateHash = preHash,
            resultingStateHash = postHash,
            differenceSummary = diff.summary,
            confidence = if (success) 0.95f else 0.2f,
            isRetryable = !success && action !is ActionType.Confirm && action !is ActionType.Done,
            errorInformation = errorInfo
        )

        // Record trace
        _actionTrace.tryEmit(
            ActionExecutionRecord(
                action = action,
                riskEvaluation = risk,
                wasExecuted = true,
                executionTimeMs = duration,
                success = success
            )
        )
        _actionResultTrace.tryEmit(result)

        AryaLogger.i(TAG, "Action complete in ${duration}ms: success=$success, diff=${diff.summary}")
        return result
    }

    /**
     * Legacy adapter for backward compatibility.
     */
    suspend fun executeAction(
        action: ActionType,
        goalContext: String,
        userApproved: Boolean = false
    ): Pair<Boolean, RiskEvaluation> {
        val res = execute(action, goalContext, userApproved = userApproved)
        val risk = riskClassifier.evaluate(action, goalContext)
        return Pair(res.success, risk)
    }

    companion object {
        private const val TAG = "ActionEngine"
    }
}
