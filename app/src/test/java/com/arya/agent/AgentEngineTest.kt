package com.arya.agent

import com.arya.actions.ActionType
import com.arya.context.social.EmotionalSignal
import com.arya.memory.MemoryType
import com.arya.perception.ScreenState
import com.arya.perception.UiNode
import com.arya.reflection.RecommendedNextStep
import com.arya.reflection.ReflectionRecord
import com.arya.reflection.ReflectionVerdict
import com.arya.security.RiskLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEngineTest {

    @Test
    fun testEngineInitializationAndBrokerBindings() {
        val engine = AgentEngine()
        assertEquals(AgentRunStatus.IDLE, engine.broker.status.value)
        assertEquals("Ready", engine.broker.telemetry.value.statusMessage)
        assertEquals(0, engine.broker.telemetry.value.stepCount)
    }

    @Test
    fun testSeamAndEmotionalContextAdaptation() {
        val engine = AgentEngine()
        val urgentGoal = "Hurry up! open Chrome immediately!"
        val emotional = engine.emotionalDetector.detect(urgentGoal)
        val policy = engine.emotionalDetector.getAdaptivePolicy(emotional)

        assertEquals(EmotionalSignal.URGENCY, emotional.dominantSignal)
        assertEquals(250L, policy.paceDelayMs)
        assertEquals(1, policy.verbosityLevel)
    }

    @Test
    fun testMemoryContextResolution() = runBlocking {
        val engine = AgentEngine()
        engine.memoryStore.processAndStore(
            statement = "Preferred search engine is DuckDuckGo",
            source = "user_preference"
        )

        val retrieved = engine.memoryStore.query("DuckDuckGo", limit = 1)
        assertEquals(1, retrieved.size)
        assertTrue(retrieved.first().content.contains("DuckDuckGo"))
    }

    @Test
    fun testEntityContextResolution() = runBlocking {
        val engine = AgentEngine()
        engine.entityManager.upsertPerson(
            name = "Sarah Connor",
            relationship = "Colleague",
            organization = "Cyberdyne"
        )

        val found = engine.entityManager.findPerson("sarah")
        assertNotNull(found)
        assertEquals("Sarah Connor", found?.name)
        assertEquals("Colleague", found?.relationship)
    }

    @Test
    fun testFull9StageActionLoopWithSimulatedScreens() = runBlocking {
        var screenCounter = 0
        val simulatedScreens = listOf(
            ScreenState(
                packageName = "com.android.launcher",
                nodes = listOf(
                    UiNode(id = "node_1", className = "android.widget.TextView", text = "Settings", isClickable = true, boundsLeft = 100, boundsTop = 200, boundsRight = 300, boundsBottom = 250)
                )
            ),
            ScreenState(
                packageName = "com.android.settings",
                nodes = listOf(
                    UiNode(id = "node_2", className = "android.widget.TextView", text = "Network & internet", isClickable = true, boundsLeft = 100, boundsTop = 300, boundsRight = 400, boundsBottom = 350)
                )
            ),
            ScreenState(
                packageName = "com.android.settings",
                nodes = listOf(
                    UiNode(id = "node_3", className = "android.widget.TextView", text = "Task objective achieved", isClickable = false, boundsLeft = 100, boundsTop = 100, boundsRight = 500, boundsBottom = 150)
                )
            )
        )

        val engine = AgentEngine(
            screenStateSupplier = {
                val idx = (screenCounter++).coerceAtMost(simulatedScreens.size - 1)
                simulatedScreens[idx]
            }
        )

        engine.startTask("open settings", delaySeconds = 0)

        // Allow coroutine execution
        var attempts = 0
        while (engine.broker.status.value != AgentRunStatus.COMPLETED && attempts < 20) {
            delay(100L)
            attempts++
        }

        assertEquals(AgentRunStatus.COMPLETED, engine.broker.status.value)
        assertTrue(engine.broker.telemetry.value.stepCount > 0)

        // Verify task completion was recorded in memory
        val memories = engine.memoryStore.query("open settings", limit = 5)
        assertTrue(memories.isNotEmpty())
    }

    @Test
    fun testRiskInterceptionAndHumanApproval() {
        val broker = AgentBroker()
        val engine = AgentEngine(broker = broker)

        val riskyAction = ActionType.Tap(label = "Confirm Transfer of $500")
        val (allowed, eval) = engine.riskManager.checkAndIntercept(riskyAction, "transfer money")

        assertEquals(false, allowed)
        assertTrue(eval.level == RiskLevel.HIGH || eval.level == RiskLevel.CRITICAL)
        assertEquals(AgentRunStatus.AWAITING_CONFIRMATION, broker.status.value)

        // Resolve approval
        engine.riskManager.resolveApproval(approved = true)
        assertEquals(AgentRunStatus.RUNNING, broker.status.value)
    }

    @Test
    fun testEmergencyStopKillSwitch() {
        val broker = AgentBroker()
        val engine = AgentEngine(broker = broker)

        broker.setStatus(AgentRunStatus.RUNNING, "Executing sensitive workflow")
        engine.stop()

        assertEquals(AgentRunStatus.STOPPED, broker.status.value)
    }

    @Test
    fun testLoopDetectionAndRecoveryFormulation() {
        val engine = AgentEngine()
        val reflection = ReflectionRecord(
            verdict = ReflectionVerdict.WRONG_TARGET,
            nextStep = RecommendedNextStep.RETRY_WITH_JITTER,
            confidence = 0.85f,
            rationale = "Target click produced zero delta"
        )

        val state = ScreenState("com.example.app")
        val recovery = engine.recoveryEngine.planRecovery(
            reflection = reflection,
            failedAction = ActionType.Tap(x = 150f, y = 250f),
            currentState = state
        )

        assertEquals(com.arya.recovery.RecoveryType.RETRY_WITH_COORDINATE_JITTER, recovery.type)
        assertTrue(recovery.actionToExecute is ActionType.Tap)
    }
}
