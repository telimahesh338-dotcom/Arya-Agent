package com.arya.recovery

import com.arya.actions.ActionType
import kotlinx.serialization.Serializable

enum class RecoveryType {
    RETRY_WITH_COORDINATE_JITTER,
    DISMISS_MODAL_OR_PERMISSION,
    HIDE_KEYBOARD_BACK,
    WAIT_FOR_NETWORK_OR_LOADING,
    RELAUNCH_CRASHED_APP,
    BACKSTEP_NAVIGATION,
    FALLBACK_SCROLL_SEARCH,
    REQUEST_HUMAN_INTERVENTION,
    ABORT_TASK
}

@Serializable
data class RecoveryAction(
    val type: RecoveryType,
    val actionToExecute: ActionType,
    val explanation: String,
    val attemptNumber: Int = 1
)
