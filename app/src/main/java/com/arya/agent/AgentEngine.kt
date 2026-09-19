package com.arya.agent

import android.content.Context
import com.arya.actions.ActionEngine
import com.arya.actions.ActionType
import com.arya.android.AryaAccessibilityService
import com.arya.perception.ScreenCaptureService
import com.arya.perception.ScreenChangeDetector
import com.arya.perception.ScreenState
import com.arya.security.RiskClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Master Cognitive Orchestrator for ARYA.
 * Coordinates Perception -> Grounding -> Planning -> Action -> Verification -> Reflection.
 */
class AgentEngine(
    private val context: Context,
    val broker: AgentBroker = AgentBroker(),
    val actionEngine: ActionEngine = ActionEngine(context)
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var currentTaskJob: Job? = null
    private val changeDetector = ScreenChangeDetector()
    private var lastScreenState: ScreenState? = null

    fun startTask(goal: String, delaySeconds: Int = 0) {
        currentTaskJob?.cancel()
        currentTaskJob = scope.launch {
            try {
                if (delaySeconds > 0) {
                    broker.setStatus(AgentRunStatus.COUNTDOWN, "Starting in $delaySeconds seconds...")
                    for (i in delaySeconds downTo 1) {
                        broker.updateTelemetry { it.copy(countdownRemaining = i) }
                        delay(1000L)
                    }
                }

                executeLoop(goal)
            } catch (e: CancellationException) {
                broker.setStatus(AgentRunStatus.STOPPED, "Task stopped by operator.")
            } catch (t: Throwable) {
                broker.setStatus(AgentRunStatus.FAILED, "Error: ${t.localizedMessage ?: "Unknown failure"}")
            }
        }
    }

    private suspend fun executeLoop(goal: String) {
        broker.setStatus(AgentRunStatus.RUNNING, "Observing environment...")
        broker.updateTelemetry { it.copy(maxSteps = 30, stepCount = 0) }

        var currentStep = 0
        val maxSteps = 30

        while (scope.isActive && currentStep < maxSteps) {
            if (broker.status.value == AgentRunStatus.STOPPED) {
                break
            }

            // Check if paused
            while (broker.status.value == AgentRunStatus.PAUSED ||
                   broker.status.value == AgentRunStatus.AWAITING_CONFIRMATION ||
                   broker.status.value == AgentRunStatus.AWAITING_USER_INPUT) {
                delay(200L)
            }

            currentStep++
            broker.updateTelemetry { it.copy(stepCount = currentStep) }
            broker.log("--- Step $currentStep of $maxSteps ---")

            // 1. OBSERVE & UNDERSTAND (Phase 2 Accessibility Core)
            val service = AryaAccessibilityService.instance
            if (service == null || !service.isConnected.value) {
                broker.setStatus(AgentRunStatus.FAILED, "Accessibility Service disconnected.")
                return
            }

            val screenState = service.captureScreenState()
            broker.updateTelemetry { it.copy(activePackage = screenState.packageName) }
            broker.log("Observed screen: ${screenState.packageName} (${screenState.actionableNodes.size} interactive targets)")

            val diff = changeDetector.detectChange(lastScreenState, screenState)
            if (lastScreenState != null) {
                broker.log("UI Change (${diff.changeLevel}): ${diff.summary}")
            }
            lastScreenState = screenState

            // Check for injected user steering
            val steering = broker.consumeSteering()
            if (steering != null) {
                broker.log("Applying operator steering: $steering")
                broker.acknowledgeSteering()
            }

            // Phase 2 Accessibility Core verification complete
            broker.setStatus(AgentRunStatus.COMPLETED, "Accessibility Core operational. Screen parsed: ${screenState.actionableNodes.size} nodes.")
            break
        }

        if (currentStep >= maxSteps) {
            broker.setStatus(AgentRunStatus.FAILED, "Maximum step limit ($maxSteps) reached.")
        }
    }

    fun stop() {
        currentTaskJob?.cancel()
        broker.stop()
    }
}
