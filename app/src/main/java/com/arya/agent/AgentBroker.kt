package com.arya.agent

import com.arya.actions.ActionType
import com.arya.security.RiskEvaluation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AgentRunStatus {
    IDLE,
    COUNTDOWN,
    RUNNING,
    PAUSED,
    AWAITING_CONFIRMATION,
    AWAITING_USER_INPUT,
    COMPLETED,
    FAILED,
    STOPPED
}

enum class SteeringState {
    NONE,
    PENDING,
    DELIVERED,
    ACKNOWLEDGED
}

data class SteeringDirective(
    val instruction: String,
    val state: SteeringState = SteeringState.PENDING,
    val timestamp: Long = System.currentTimeMillis()
)

data class ConfirmationRequest(
    val action: ActionType,
    val risk: RiskEvaluation,
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentTelemetry(
    val stepCount: Int = 0,
    val maxSteps: Int = 30,
    val currentSubgoal: String = "",
    val activePackage: String = "",
    val statusMessage: String = "Ready",
    val countdownRemaining: Int = 0
)

/**
 * Clean-room reactive state broker for ARYA.
 * Coordinates user commands, steering directives, risk confirmations, and UI telemetry.
 */
class AgentBroker {

    private val _status = MutableStateFlow(AgentRunStatus.IDLE)
    val status: StateFlow<AgentRunStatus> = _status.asStateFlow()

    private val _telemetry = MutableStateFlow(AgentTelemetry())
    val telemetry: StateFlow<AgentTelemetry> = _telemetry.asStateFlow()

    private val _steeringDirective = MutableStateFlow<SteeringDirective?>(null)
    val steeringDirective: StateFlow<SteeringDirective?> = _steeringDirective.asStateFlow()

    private val _confirmationRequest = MutableStateFlow<ConfirmationRequest?>(null)
    val confirmationRequest: StateFlow<ConfirmationRequest?> = _confirmationRequest.asStateFlow()

    private val _userQuestion = MutableStateFlow<String?>(null)
    val userQuestion: StateFlow<String?> = _userQuestion.asStateFlow()

    private val _logStream = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val logStream: SharedFlow<String> = _logStream.asSharedFlow()

    fun setStatus(newStatus: AgentRunStatus, message: String = "") {
        _status.value = newStatus
        if (message.isNotBlank()) {
            updateStatusMessage(message)
        }
    }

    fun updateStatusMessage(message: String) {
        _telemetry.value = _telemetry.value.copy(statusMessage = message)
        _logStream.tryEmit(message)
    }

    fun updateTelemetry(transform: (AgentTelemetry) -> AgentTelemetry) {
        _telemetry.value = transform(_telemetry.value)
    }

    fun log(msg: String) {
        _logStream.tryEmit(msg)
    }

    fun requestConfirmation(request: ConfirmationRequest) {
        _confirmationRequest.value = request
        _status.value = AgentRunStatus.AWAITING_CONFIRMATION
        log("Human approval required: ${request.risk.reason}")
    }

    fun resolveConfirmation(approved: Boolean) {
        _confirmationRequest.value = null
        if (approved) {
            _status.value = AgentRunStatus.RUNNING
            log("Action approved by operator.")
        } else {
            _status.value = AgentRunStatus.RUNNING
            log("Action denied by operator. Replanning...")
        }
    }

    fun injectSteering(instruction: String) {
        _steeringDirective.value = SteeringDirective(instruction, SteeringState.PENDING)
        log("Steering injected: $instruction")
    }

    fun consumeSteering(): String? {
        val current = _steeringDirective.value ?: return null
        if (current.state == SteeringState.PENDING) {
            _steeringDirective.value = current.copy(state = SteeringState.DELIVERED)
            return current.instruction
        }
        return null
    }

    fun acknowledgeSteering() {
        val current = _steeringDirective.value ?: return
        _steeringDirective.value = current.copy(state = SteeringState.ACKNOWLEDGED)
    }

    fun askUser(question: String) {
        _userQuestion.value = question
        _status.value = AgentRunStatus.AWAITING_USER_INPUT
        log("Asking operator: $question")
    }

    fun answerUserQuestion(answer: String) {
        _userQuestion.value = null
        _status.value = AgentRunStatus.RUNNING
        injectSteering("User response: $answer")
    }

    fun stop() {
        _status.value = AgentRunStatus.STOPPED
        _confirmationRequest.value = null
        _userQuestion.value = null
        log("EMERGENCY STOP TRIGGERED")
    }

    fun reset() {
        _status.value = AgentRunStatus.IDLE
        _telemetry.value = AgentTelemetry()
        _steeringDirective.value = null
        _confirmationRequest.value = null
        _userQuestion.value = null
    }
}
