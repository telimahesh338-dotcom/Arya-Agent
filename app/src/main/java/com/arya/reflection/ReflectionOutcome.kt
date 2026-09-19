package com.arya.reflection

import kotlinx.serialization.Serializable

enum class ReflectionVerdict {
    SUCCESS,
    PARTIAL_SUCCESS,
    WRONG_TARGET,
    UNEXPECTED_STATE,
    POPUP_ENCOUNTERED,
    KEYBOARD_OCCLUSION,
    NAVIGATION_DRIFT,
    LOADING_PROGRESS,
    APP_CRASH,
    FAILURE
}

enum class RecommendedNextStep {
    PROCEED,
    RETRY_WITH_JITTER,
    DISMISS_POPUP,
    HIDE_KEYBOARD,
    WAIT_FOR_LOAD,
    REPLAN,
    RECOVER_BACKSTEP,
    ABORT
}

@Serializable
data class ReflectionRecord(
    val verdict: ReflectionVerdict,
    val nextStep: RecommendedNextStep,
    val confidence: Float,
    val rationale: String,
    val consecutiveFailures: Int = 0,
    val isLoopDetected: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
