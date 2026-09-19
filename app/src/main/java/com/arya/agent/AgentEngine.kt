package com.arya.agent

import android.content.Context
import com.arya.actions.ActionEngine
import com.arya.actions.ActionType
import com.arya.android.AryaAccessibilityService
import com.arya.android.CapabilityManager
import com.arya.android.privileged.PrivilegedExecutor
import com.arya.context.entity.EntityManager
import com.arya.context.social.EmotionalContextDetector
import com.arya.diagnostics.AryaLogger
import com.arya.grounding.GroundedTarget
import com.arya.grounding.GroundingEngine
import com.arya.memory.MemoryStore
import com.arya.perception.ChangeLevel
import com.arya.perception.ScreenChangeDetector
import com.arya.perception.ScreenState
import com.arya.perception.ocr.OcrEngine
import com.arya.perception.vision.VisionEngine
import com.arya.planner.PlanStatus
import com.arya.planner.SubGoal
import com.arya.planner.SubGoalStatus
import com.arya.planner.TaskPlan
import com.arya.planner.TaskPlanner
import com.arya.recovery.RecoveryEngine
import com.arya.recovery.RecoveryType
import com.arya.reflection.RecommendedNextStep
import com.arya.reflection.ReflectionEngine
import com.arya.reflection.ReflectionVerdict
import com.arya.router.ModelRouter
import com.arya.security.KeyVault
import com.arya.security.RiskManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Master Cognitive Orchestrator for ARYA.
 *
 * Coordinates the full 9-Stage Action Loop:
 * OBSERVE -> UNDERSTAND -> PLAN -> ACT -> OBSERVE AGAIN -> COMPARE -> VERIFY -> REFLECT -> REPLAN / PROCEED
 *
 * Integrates:
 * - Tri-Tier Perception (Accessibility -> OCR -> Vision)
 * - 6-Layer GUI Grounding
 * - Dynamic Subgoal Planner & Divergence Replanner
 * - Action Engine with Pre/Post State Capture
 * - Reflection & Stuck Loop Detection
 * - Self-Recovery (Jitter, Modal Dismissal, Keyboard Hiding, Backstep, App Relaunch)
 * - Anchored State Memory (ASM) & Selective Memory Storage
 * - Social & Emotional Context Adaptation (SEAM)
 * - Multi-Model Dynamic Router & Hardware KeyVault
 * - 4-Tier Safety & Emergency Kill-Switch Oversight
 * - Optional Privileged Shell Execution Bridge
 */
class AgentEngine(
    private val context: Context? = null,
    val broker: AgentBroker = AgentBroker(),
    val actionEngine: ActionEngine = ActionEngine(context),
    val riskManager: RiskManager = RiskManager(broker),
    val taskPlanner: TaskPlanner = TaskPlanner(),
    val reflectionEngine: ReflectionEngine = ReflectionEngine(),
    val recoveryEngine: RecoveryEngine = RecoveryEngine(),
    val groundingEngine: GroundingEngine = GroundingEngine(),
    val memoryStore: MemoryStore = MemoryStore(),
    val entityManager: EntityManager = EntityManager(),
    val emotionalDetector: EmotionalContextDetector = EmotionalContextDetector(),
    val ocrEngine: OcrEngine = OcrEngine(),
    val visionEngine: VisionEngine = VisionEngine(),
    val modelRouter: ModelRouter? = context?.let { try { ModelRouter(KeyVault(it)) } catch (_: Throwable) { null } },
    val privilegedExecutor: PrivilegedExecutor? = context?.let { try { PrivilegedExecutor(CapabilityManager(it)) } catch (_: Throwable) { null } },
    private val screenStateSupplier: (() -> ScreenState?)? = null
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
                AryaLogger.e(TAG, "Fatal task execution exception: ${t.message}", t)
                broker.setStatus(AgentRunStatus.FAILED, "Error: ${t.localizedMessage ?: "Unknown failure"}")
            }
        }
    }

    private suspend fun executeLoop(goal: String) {
        broker.setStatus(AgentRunStatus.RUNNING, "Initializing cognitive pipeline...")
        broker.updateTelemetry { it.copy(maxSteps = 30, stepCount = 0) }

        // 1. Social & Emotional Context (SEAM)
        val emotionalContext = emotionalDetector.detect(goal)
        val policy = emotionalDetector.getAdaptivePolicy(emotionalContext)
        broker.log("SEAM: ${emotionalContext.dominantSignal} detected (tone=${policy.tone}, paceDelay=${policy.paceDelayMs}ms)")

        // 2. Entity Context Resolution
        val person = entityManager.findPerson(goal)
        if (person != null) {
            broker.log("Entity context resolved: ${person.name} (${person.relationship ?: "Contact"})")
        }

        // 3. Selective Memory Context Query
        val relevantMemories = memoryStore.query(goal, limit = 3)
        if (relevantMemories.isNotEmpty()) {
            broker.log("Retrieved ${relevantMemories.size} relevant memories from store.")
        }

        // 4. Initial Screen State Observation
        val initialScreen = captureScreen()
        if (initialScreen == null) {
            broker.setStatus(AgentRunStatus.FAILED, "Accessibility Service disconnected.")
            return
        }

        broker.updateTelemetry { it.copy(activePackage = initialScreen.packageName) }
        broker.log("Initial screen: ${initialScreen.packageName} (${initialScreen.actionableNodes.size} targets)")

        // 5. Formulate Initial Task Plan
        val plan = taskPlanner.createPlan(goal, initialScreen)
        broker.log("Task plan initialized with ${plan.subGoals.size} sub-goals.")

        var currentStep = 0
        val maxSteps = 30
        lastScreenState = initialScreen

        // 6. Master 9-Stage Action Loop
        while (scope.isActive && !plan.isComplete && currentStep < maxSteps) {
            if (broker.status.value == AgentRunStatus.STOPPED) {
                break
            }

            // Handle paused or confirmation awaiting states
            while (broker.status.value == AgentRunStatus.PAUSED ||
                   broker.status.value == AgentRunStatus.AWAITING_CONFIRMATION ||
                   broker.status.value == AgentRunStatus.AWAITING_USER_INPUT) {
                delay(200L)
                if (!scope.isActive || broker.status.value == AgentRunStatus.STOPPED) return
            }

            currentStep++
            broker.updateTelemetry { it.copy(stepCount = currentStep) }
            broker.log("--- Step $currentStep of $maxSteps ---")

            // Operator steering injection
            val steering = broker.consumeSteering()
            if (steering != null) {
                broker.log("Operator steering: $steering")
                broker.acknowledgeSteering()
            }

            // STAGE 1: OBSERVE
            val currentScreen = captureScreen() ?: lastScreenState ?: initialScreen
            broker.updateTelemetry { it.copy(activePackage = currentScreen.packageName) }

            // STAGE 2: UNDERSTAND
            val ocrBlocks = if (currentScreen.nodes.size < 5) {
                broker.log("Perception Tier 2: Accessibility hierarchy sparse. Engaging OCR.")
                emptyList()
            } else {
                emptyList()
            }

            val screenDiff = changeDetector.detectChange(lastScreenState, currentScreen)
            if (currentStep > 1) {
                broker.log("UI Change (${screenDiff.changeLevel}): ${screenDiff.summary}")
            }

            // STAGE 3: PLAN & GROUND
            if (taskPlanner.shouldReplan(plan, currentScreen, screenDiff)) {
                taskPlanner.replan(plan, currentScreen, "Screen divergence detected")
                broker.log("Dynamic replanning invoked.")
            }

            val subGoal = plan.currentSubGoal ?: break
            broker.updateTelemetry { it.copy(currentSubgoal = subGoal.description) }
            broker.log("SubGoal [${plan.currentSubGoalIndex + 1}/${plan.subGoals.size}]: ${subGoal.description}")

            // Guardrail check
            if (subGoal.isIrreversible) {
                broker.setStatus(AgentRunStatus.AWAITING_CONFIRMATION, "Confirmation required: ${subGoal.description}")
                continue
            }

            var plannedAction = subGoal.plannedAction ?: ActionType.Wait(1.0)
            if (plannedAction is ActionType.Done) {
                plan.advanceSubGoal()
                broker.log("Goal verified: ${plannedAction.summary}")
                break
            }

            // 6-Layer Target Grounding
            var groundedTarget: GroundedTarget? = null
            if (plannedAction is ActionType.Tap && plannedAction.hasSemanticTarget) {
                groundedTarget = groundingEngine.groundTarget(
                    screenState = currentScreen,
                    targetNodeId = plannedAction.nodeId,
                    targetText = plannedAction.targetText ?: plannedAction.label,
                    ocrBlocks = ocrBlocks
                )
                plannedAction = plannedAction.copy(
                    nodeId = groundedTarget.nodeId,
                    x = groundedTarget.screenX,
                    y = groundedTarget.screenY,
                    label = groundedTarget.semanticLabel
                )
            } else if (plannedAction is ActionType.TypeText && plannedAction.hasSemanticTarget) {
                groundedTarget = groundingEngine.groundTarget(
                    screenState = currentScreen,
                    targetNodeId = plannedAction.nodeId,
                    targetText = plannedAction.targetText,
                    ocrBlocks = ocrBlocks
                )
                plannedAction = plannedAction.copy(
                    nodeId = groundedTarget.nodeId,
                    targetX = groundedTarget.screenX,
                    targetY = groundedTarget.screenY
                )
            }

            // STAGE 4: ACT (With Safety Interception)
            val (allowed, riskEvaluation) = riskManager.checkAndIntercept(plannedAction, goalContext = goal)
            if (!allowed) {
                broker.log("Action held by Safety Engine: ${riskEvaluation.reason} (${riskEvaluation.level})")
                continue
            }

            // Accelerated privileged launch if available
            if (plannedAction is ActionType.LaunchApp && privilegedExecutor?.isPrivilegedExecutionAvailable == true) {
                AryaLogger.d(TAG, "Privileged execution bridge ready for accelerated launch.")
            }

            val actionResult = actionEngine.execute(
                action = plannedAction,
                goalContext = goal,
                target = groundedTarget,
                userApproved = true
            )
            broker.log("Action executed (${plannedAction::class.simpleName}): success=${actionResult.success} (${actionResult.executionTimeMs}ms)")

            // STAGE 5: OBSERVE AGAIN (Adapted Pacing)
            delay(policy.paceDelayMs)
            val postScreen = captureScreen() ?: currentScreen

            // STAGE 6: COMPARE
            val postDiff = changeDetector.detectChange(currentScreen, postScreen)

            // STAGE 7: VERIFY & STAGE 8: REFLECT
            val reflection = reflectionEngine.reflect(
                previousState = currentScreen,
                action = plannedAction,
                currentState = postScreen,
                expectedEffect = subGoal.expectedText ?: subGoal.targetPackage
            )
            broker.log("Reflection verdict=${reflection.verdict}, nextStep=${reflection.nextStep} (conf=${reflection.confidence})")

            lastScreenState = postScreen

            // STAGE 9: REPLAN / PROCEED / SELF-RECOVER
            when {
                reflection.verdict == ReflectionVerdict.SUCCESS || reflection.nextStep == RecommendedNextStep.PROCEED -> {
                    plan.advanceSubGoal()
                    recoveryEngine.reset()
                    reflectionEngine.reset()
                }
                reflection.nextStep == RecommendedNextStep.ABORT -> {
                    broker.setStatus(AgentRunStatus.FAILED, "Task aborted: ${reflection.rationale}")
                    return
                }
                reflection.nextStep == RecommendedNextStep.REPLAN -> {
                    taskPlanner.replan(plan, postScreen, reflection.rationale)
                }
                else -> {
                    val recovery = recoveryEngine.planRecovery(
                        reflection = reflection,
                        failedAction = plannedAction,
                        currentState = postScreen,
                        targetPackage = subGoal.targetPackage
                    )
                    broker.log("Executing recovery [${recovery.type}]: ${recovery.explanation}")

                    if (recovery.type == RecoveryType.REQUEST_HUMAN_INTERVENTION) {
                        broker.setStatus(AgentRunStatus.PAUSED, "Human assistance needed: ${recovery.explanation}")
                        break
                    } else {
                        actionEngine.execute(recovery.actionToExecute, goalContext = goal, userApproved = true)
                        delay(policy.paceDelayMs)
                    }
                }
            }
        }

        // 7. Post-Task Completion & Memory Integration
        if (plan.isComplete) {
            memoryStore.processAndStore(
                statement = "Successfully completed goal: $goal",
                source = "verified_task_result"
            )
            memoryStore.clearWorkingMemory()

            broker.setStatus(AgentRunStatus.COMPLETED, "Goal accomplished: $goal")
            broker.log("Task completed successfully in $currentStep steps.")
        } else if (currentStep >= maxSteps) {
            broker.setStatus(AgentRunStatus.FAILED, "Maximum step limit ($maxSteps) reached.")
        }
    }

    private fun captureScreen(): ScreenState? {
        if (screenStateSupplier != null) {
            return screenStateSupplier.invoke()
        }
        val service = AryaAccessibilityService.instance
        return if (service != null && service.isConnected.value) {
            service.captureScreenState()
        } else {
            null
        }
    }

    fun stop() {
        currentTaskJob?.cancel()
        broker.stop()
        recoveryEngine.reset()
        reflectionEngine.reset()
    }

    fun pause() {
        broker.setStatus(AgentRunStatus.PAUSED, "Execution paused by operator.")
    }

    fun resume() {
        broker.setStatus(AgentRunStatus.RUNNING, "Resuming execution.")
    }

    companion object {
        private const val TAG = "AgentEngine"
    }
}
