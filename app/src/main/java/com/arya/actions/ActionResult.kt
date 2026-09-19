package com.arya.actions

import com.arya.grounding.GroundedTarget
import kotlinx.serialization.Serializable

/**
 * Rich result produced by the ActionEngine after every action execution.
 * Enforces the Observe -> Act -> Observe paradigm with pre/post state auditing.
 */
@Serializable
data class ActionResult(
    val action: ActionType,
    val success: Boolean,
    val failureReason: String? = null,
    val target: GroundedTarget? = null,
    val executionTimeMs: Long = 0L,
    val previousStateHash: String? = null,
    val resultingStateHash: String? = null,
    val differenceSummary: String? = null,
    val confidence: Float = 1.0f,
    val isRetryable: Boolean = false,
    val errorInformation: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
