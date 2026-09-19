package com.arya.actions

import android.content.Context
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
 * Mid-level action engine that performs risk checks, coordinates approvals,
 * and delegates to the low-level GestureController.
 */
class ActionEngine(
    context: Context,
    private val riskClassifier: RiskClassifier = RiskClassifier(),
    private val gestureController: GestureController = GestureController(context)
) {
    private val _actionTrace = MutableSharedFlow<ActionExecutionRecord>(extraBufferCapacity = 64)
    val actionTrace: SharedFlow<ActionExecutionRecord> = _actionTrace.asSharedFlow()

    suspend fun executeAction(
        action: ActionType,
        goalContext: String,
        userApproved: Boolean = false
    ): Pair<Boolean, RiskEvaluation> {
        val risk = riskClassifier.evaluate(action, goalContext)

        if (risk.requiresApproval && !userApproved) {
            AryaLogger.w("ActionEngine", "Action requires human approval: ${risk.reason} (Risk: ${risk.level})")
            return Pair(false, risk)
        }

        AryaLogger.i("ActionEngine", "Dispatching action: $action")
        val startTime = System.currentTimeMillis()
        val success = try {
            gestureController.execute(action)
        } catch (t: Throwable) {
            AryaLogger.e("ActionEngine", "Gesture execution failed with exception: ${t.message}", t)
            false
        }
        val duration = System.currentTimeMillis() - startTime
        AryaLogger.i("ActionEngine", "Action execution completed in ${duration}ms (success=$success)")

        val record = ActionExecutionRecord(
            action = action,
            riskEvaluation = risk,
            wasExecuted = true,
            executionTimeMs = duration,
            success = success
        )
        _actionTrace.tryEmit(record)

        return Pair(success, risk)
    }
}
