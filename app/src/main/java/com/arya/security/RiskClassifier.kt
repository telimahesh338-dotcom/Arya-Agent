package com.arya.security

import com.arya.actions.ActionType
import kotlinx.serialization.Serializable

@Serializable
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class RiskEvaluation(
    val level: RiskLevel,
    val requiresApproval: Boolean,
    val reason: String
)

/**
 * Classifies planned actions into risk categories to prevent unauthorized destructive operations.
 */
class RiskClassifier {

    private val criticalKeywords = setOf(
        "pay", "payment", "buy", "purchase", "checkout", "order", "transfer",
        "delete", "remove", "erase", "uninstall", "format",
        "password", "pin", "credential", "credit card", "bank", "otp"
    )

    private val highRiskKeywords = setOf(
        "submit", "send", "confirm order", "book now", "apply changes",
        "save card", "update profile", "change settings"
    )

    fun evaluate(action: ActionType, goalContext: String): RiskEvaluation {
        return when (action) {
            is ActionType.Confirm -> {
                RiskEvaluation(
                    level = action.riskLevel,
                    requiresApproval = true,
                    reason = action.prompt
                )
            }
            is ActionType.TakeOver -> {
                RiskEvaluation(
                    level = RiskLevel.CRITICAL,
                    requiresApproval = true,
                    reason = action.reason
                )
            }
            is ActionType.TypeText -> {
                val lowerText = action.text.lowercase()
                if (criticalKeywords.any { lowerText.contains(it) }) {
                    RiskEvaluation(
                        level = RiskLevel.HIGH,
                        requiresApproval = true,
                        reason = "Typing potentially sensitive content: ${action.text.take(15)}..."
                    )
                } else {
                    RiskEvaluation(RiskLevel.LOW, false, "Standard text entry")
                }
            }
            is ActionType.Tap -> {
                val label = action.label?.lowercase() ?: ""
                val lowerGoal = goalContext.lowercase()

                if (criticalKeywords.any { label.contains(it) || lowerGoal.contains(it) }) {
                    RiskEvaluation(
                        level = RiskLevel.HIGH,
                        requiresApproval = true,
                        reason = "Action targets high-impact operation: ${action.label}"
                    )
                } else if (highRiskKeywords.any { label.contains(it) }) {
                    RiskEvaluation(
                        level = RiskLevel.MEDIUM,
                        requiresApproval = false,
                        reason = "Action triggers workflow step: ${action.label}"
                    )
                } else {
                    RiskEvaluation(RiskLevel.LOW, false, "Standard UI interaction")
                }
            }
            is ActionType.ClearText -> {
                RiskEvaluation(RiskLevel.LOW, false, "Clearing text entry field")
            }
            is ActionType.Drag -> {
                RiskEvaluation(RiskLevel.LOW, false, "Standard drag gesture")
            }
            is ActionType.Retry -> {
                evaluate(action.originalAction, goalContext)
            }
            is ActionType.Cancel -> {
                RiskEvaluation(RiskLevel.LOW, false, "Operator cancellation signal")
            }
            is ActionType.PressBack,
            is ActionType.PressHome,
            is ActionType.Scroll,
            is ActionType.Swipe,
            is ActionType.Wait,
            is ActionType.LongTap,
            is ActionType.LaunchApp,
            is ActionType.Done,
            is ActionType.Fail -> {
                RiskEvaluation(RiskLevel.LOW, false, "Safe navigational primitive")
            }
        }
    }
}
