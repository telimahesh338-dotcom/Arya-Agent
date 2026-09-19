package com.arya.security

import com.arya.actions.ActionType
import com.arya.agent.AgentBroker
import com.arya.agent.AgentRunStatus
import com.arya.diagnostics.AryaLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class RiskApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val action: ActionType,
    val riskEvaluation: RiskEvaluation,
    val promptMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Master Safety & Risk Approval Manager for ARYA.
 *
 * Guarantees:
 * 1. Hard approval stop for payments, purchases, deletions, and credential modifications.
 * 2. Instant Emergency STOP kill-switch that aborts execution immediately.
 * 3. Never bypasses Android security, biometric prompts, or user consent dialogs.
 */
class RiskManager(
    private val agentBroker: AgentBroker,
    private val classifier: RiskClassifier = RiskClassifier()
) {

    private val _pendingApproval = MutableStateFlow<RiskApprovalRequest?>(null)
    val pendingApproval: StateFlow<RiskApprovalRequest?> = _pendingApproval.asStateFlow()

    /**
     * Evaluates action risk. If human approval is required, transitions agent into
     * AWAITING_CONFIRMATION state and publishes an approval request.
     */
    fun checkAndIntercept(
        action: ActionType,
        goalContext: String,
        userPreApproved: Boolean = false
    ): Pair<Boolean, RiskEvaluation> {
        val evaluation = classifier.evaluate(action, goalContext)

        if (evaluation.requiresApproval && !userPreApproved) {
            AryaLogger.w(TAG, "Operation intercepted by RiskManager: ${evaluation.reason} (${evaluation.level})")

            val request = RiskApprovalRequest(
                action = action,
                riskEvaluation = evaluation,
                promptMessage = buildApprovalPrompt(action, evaluation)
            )

            _pendingApproval.value = request
            agentBroker.setStatus(
                AgentRunStatus.AWAITING_CONFIRMATION,
                "Human approval required: ${evaluation.reason}"
            )

            return Pair(false, evaluation)
        }

        return Pair(true, evaluation)
    }

    /**
     * Resolves a pending human confirmation.
     */
    fun resolveApproval(approved: Boolean, operatorNote: String? = null) {
        val current = _pendingApproval.value
        _pendingApproval.value = null

        if (current == null) return

        if (approved) {
            AryaLogger.i(TAG, "Action approved by operator: ${current.action::class.simpleName}")
            agentBroker.setStatus(AgentRunStatus.RUNNING, "Approved by operator. Resuming execution.")
        } else {
            AryaLogger.w(TAG, "Action denied by operator: ${current.action::class.simpleName} (Note: $operatorNote)")
            agentBroker.setStatus(AgentRunStatus.PAUSED, "Action denied by operator.")
        }
    }

    /**
     * Emergency Kill-Switch: immediately aborts active actions, gestures, and coroutine tasks.
     */
    fun triggerEmergencyStop(source: String = "Operator HUD") {
        AryaLogger.e(TAG, "EMERGENCY STOP TRIGGERED from $source! Aborting all operations immediately.")
        _pendingApproval.value = null
        agentBroker.stop()
    }

    private fun buildApprovalPrompt(action: ActionType, eval: RiskEvaluation): String {
        return when (eval.level) {
            RiskLevel.CRITICAL -> "CRITICAL SAFETY GUARD: ARYA is about to execute a potentially destructive or financial action (${eval.reason}). Do you grant explicit permission?"
            RiskLevel.HIGH -> "CONFIRMATION REQUIRED: ${eval.reason}. Tap Confirm to proceed or Cancel to halt."
            else -> "Action verification: ${eval.reason}"
        }
    }

    companion object {
        private const val TAG = "RiskManager"
    }
}
