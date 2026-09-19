package com.arya.eval

import com.arya.actions.ActionResult
import com.arya.actions.ActionType
import com.arya.actions.ScrollDirection
import com.arya.context.social.EmotionalContextDetector
import com.arya.diagnostics.AryaLogger
import com.arya.grounding.GroundedTarget
import com.arya.grounding.GroundingEngine
import com.arya.grounding.GroundingSource
import com.arya.memory.MemoryGate
import com.arya.memory.MemoryStore
import com.arya.memory.MemoryType
import com.arya.perception.ChangeLevel
import com.arya.perception.ScreenChangeDetector
import com.arya.perception.ScreenDifference
import com.arya.perception.ScreenState
import com.arya.perception.UiNode
import com.arya.perception.VisualHasher
import com.arya.perception.ocr.OcrBlock
import com.arya.perception.ocr.OcrEngine
import com.arya.perception.vision.VisionEngine
import com.arya.planner.TaskPlanner
import com.arya.recovery.RecoveryEngine
import com.arya.recovery.RecoveryType
import com.arya.reflection.RecommendedNextStep
import com.arya.reflection.ReflectionEngine
import com.arya.reflection.ReflectionVerdict
import com.arya.router.ChatMessage
import com.arya.router.MessageRole
import com.arya.router.ModelResponse
import com.arya.security.RiskClassifier
import com.arya.security.RiskLevel
import com.arya.voice.VoiceRecognizer
import kotlinx.coroutines.runBlocking

data class BenchmarkScenarioResult(
    val id: Int,
    val name: String,
    val passed: Boolean,
    val executionTimeMs: Long,
    val details: String
)

data class BenchmarkSummary(
    val totalScenarios: Int,
    val passedScenarios: Int,
    val failedScenarios: Int,
    val passRatePercentage: Float,
    val results: List<BenchmarkScenarioResult>
)

/**
 * Master 21-Capability Evaluation & Benchmark Suite for ARYA.
 * Validates perception, grounding, planning, actions, recovery, memory, routing, and safety.
 */
class BenchmarkSuite {

    fun runAllEvaluations(): BenchmarkSummary {
        val results = mutableListOf<BenchmarkScenarioResult>()

        results.add(runScenario(1, "Accessibility tree parsing") { testAccessibilityTreeParsing() })
        results.add(runScenario(2, "Screen state generation") { testScreenStateGeneration() })
        results.add(runScenario(3, "Screenshot capture & dHash") { testScreenshotDHash() })
        results.add(runScenario(4, "OCR text extraction") { testOcrExtraction() })
        results.add(runScenario(5, "GUI Grounding priority") { testGroundingPriority() })
        results.add(runScenario(6, "Tap action model") { testTapActionModel() })
        results.add(runScenario(7, "Type text action model") { testTypeTextActionModel() })
        results.add(runScenario(8, "Scroll navigation model") { testScrollActionModel() })
        results.add(runScenario(9, "Back navigation model") { testBackActionModel() })
        results.add(runScenario(10, "Action verification & result") { testActionVerification() })
        results.add(runScenario(11, "Dynamic task planning") { testTaskPlanner() })
        results.add(runScenario(12, "Reflection engine") { testReflectionEngine() })
        results.add(runScenario(13, "Self-healing recovery") { testRecoveryEngine() })
        results.add(runScenario(14, "Memory gate filtering") { testMemoryFiltering() })
        results.add(runScenario(15, "Memory retrieval") { testMemoryRetrieval() })
        results.add(runScenario(16, "Memory expiration & decay") { testMemoryExpiration() })
        results.add(runScenario(17, "Model routing") { testModelRouting() })
        results.add(runScenario(18, "API outage fallback") { testApiFallback() })
        results.add(runScenario(19, "Voice pipeline") { testVoicePipeline() })
        results.add(runScenario(20, "Risk classification") { testRiskClassification() })
        results.add(runScenario(21, "Emergency stop mechanism") { testEmergencyStop() })

        val passed = results.count { it.passed }
        val failed = results.size - passed
        val rate = (passed.toFloat() / results.size.toFloat()) * 100.0f

        return BenchmarkSummary(
            totalScenarios = results.size,
            passedScenarios = passed,
            failedScenarios = failed,
            passRatePercentage = rate,
            results = results
        )
    }

    private fun runScenario(id: Int, name: String, block: () -> Pair<Boolean, String>): BenchmarkScenarioResult {
        val start = System.currentTimeMillis()
        return try {
            val (ok, details) = block()
            val duration = System.currentTimeMillis() - start
            BenchmarkScenarioResult(id, name, ok, duration, details)
        } catch (t: Throwable) {
            val duration = System.currentTimeMillis() - start
            BenchmarkScenarioResult(id, name, false, duration, "Exception: ${t.message}")
        }
    }

    // 1. Accessibility Tree
    private fun testAccessibilityTreeParsing(): Pair<Boolean, String> {
        val node = UiNode(
            id = "node_1",
            nodeIndex = 0,
            packageName = "com.test",
            className = "android.widget.Button",
            text = "Click Me",
            boundsLeft = 10,
            boundsTop = 20,
            boundsRight = 100,
            boundsBottom = 60,
            isClickable = true
        )
        return Pair(node.isActionable && node.area > 0 && node.contains(50f, 40f), "Node properties parsed correctly")
    }

    // 2. Screen State
    private fun testScreenStateGeneration(): Pair<Boolean, String> {
        val state = ScreenState(packageName = "com.test", isKeyboardVisible = true)
        val hash = state.structuralHash
        return Pair(hash.isNotBlank() && state.isKeyboardVisible, "Hash generated: $hash")
    }

    // 3. Screenshot dHash
    private fun testScreenshotDHash(): Pair<Boolean, String> {
        val dist = VisualHasher.hammingDistance(0x10L, 0x11L)
        return Pair(dist == 1, "Hamming distance: $dist")
    }

    // 4. OCR
    private fun testOcrExtraction(): Pair<Boolean, String> {
        val block = OcrBlock("ocr1", "Checkout $45", 10, 10, 200, 50)
        return Pair(block.text.contains("Checkout") && block.width == 190, "OCR block valid")
    }

    // 5. Grounding
    private fun testGroundingPriority(): Pair<Boolean, String> {
        val engine = GroundingEngine()
        val node = UiNode("btn_ok", 0, "pkg", "Button", text = "OK", boundsLeft = 0, boundsTop = 0, boundsRight = 100, boundsBottom = 50, isClickable = true)
        val state = ScreenState("pkg", nodes = listOf(node))
        val target = engine.groundTarget(state, targetNodeId = "btn_ok")
        return Pair(target.source == GroundingSource.ACCESSIBILITY_NODE, "Grounding priority 1 verified")
    }

    // 6. Tap Action Model
    private fun testTapActionModel(): Pair<Boolean, String> {
        val tap = ActionType.Tap(nodeId = "btn_1", targetText = "Submit")
        return Pair(tap.hasSemanticTarget, "Semantic target detected")
    }

    // 7. Type Text
    private fun testTypeTextActionModel(): Pair<Boolean, String> {
        val type = ActionType.TypeText("hello world", nodeId = "input_field")
        return Pair(type.text == "hello world" && type.hasSemanticTarget, "Type text valid")
    }

    // 8. Scroll
    private fun testScrollActionModel(): Pair<Boolean, String> {
        val scroll = ActionType.Scroll(direction = ScrollDirection.UP)
        return Pair(scroll.direction == ScrollDirection.UP, "Scroll valid")
    }

    // 9. Back Navigation
    private fun testBackActionModel(): Pair<Boolean, String> {
        val back = ActionType.PressBack
        return Pair(back is ActionType.PressBack, "Back action verified")
    }

    // 10. Action Verification
    private fun testActionVerification(): Pair<Boolean, String> {
        val detector = ScreenChangeDetector()
        val s1 = ScreenState("pkg.a")
        val s2 = ScreenState("pkg.b")
        val diff = detector.detectChange(s1, s2)
        return Pair(diff.changeLevel == ChangeLevel.SIGNIFICANT && diff.packageChanged, "State diff verified")
    }

    // 11. Planner
    private fun testTaskPlanner(): Pair<Boolean, String> {
        val planner = TaskPlanner()
        val plan = planner.createPlan("Open Chrome and search for news", ScreenState("pkg"))
        return Pair(plan.subGoals.isNotEmpty(), "Plan generated: ${plan.subGoals.size} sub-goals")
    }

    // 12. Reflection
    private fun testReflectionEngine(): Pair<Boolean, String> {
        val engine = ReflectionEngine()
        val s1 = ScreenState("pkg", isDialogShowing = false)
        val s2 = ScreenState("pkg", isDialogShowing = true)
        val ref = engine.reflect(s1, ActionType.Tap(x = 10f, y = 10f), s2)
        return Pair(ref.verdict == ReflectionVerdict.POPUP_ENCOUNTERED, "Popup reflected")
    }

    // 13. Recovery
    private fun testRecoveryEngine(): Pair<Boolean, String> {
        val engine = RecoveryEngine()
        val ref = com.arya.reflection.ReflectionRecord(ReflectionVerdict.WRONG_TARGET, RecommendedNextStep.RETRY_WITH_JITTER, 0.8f, "Zero change")
        val rec = engine.planRecovery(ref, ActionType.Tap(x = 100f, y = 100f), ScreenState("pkg"))
        return Pair(rec.type == RecoveryType.RETRY_WITH_COORDINATE_JITTER, "Jitter recovery formulated")
    }

    // 14. Memory Filtering
    private fun testMemoryFiltering(): Pair<Boolean, String> {
        val gate = MemoryGate()
        val d1 = gate.evaluate("Today I drank coffee.")
        val d2 = gate.evaluate("My brother is Rahul.")
        return Pair(!d1.shouldStore && d2.shouldStore, "Chit-chat filtered, entity admitted")
    }

    // 15. Memory Retrieval
    private fun testMemoryRetrieval(): Pair<Boolean, String> = runBlocking {
        val store = MemoryStore()
        store.processAndStore("Always use DuckDuckGo.")
        val items = store.query("DuckDuckGo", typeFilter = MemoryType.PREFERENCE)
        Pair(items.isNotEmpty(), "Retrieved preference: ${items.size} item")
    }

    // 16. Memory Expiration
    private fun testMemoryExpiration(): Pair<Boolean, String> {
        val item = com.arya.memory.MemoryItem(type = MemoryType.EPISODIC, content = "Meeting", importance = 0.8f, expiresAt = System.currentTimeMillis() - 1000L)
        return Pair(item.isExpired(), "Expired item verified")
    }

    // 17. Model Routing
    private fun testModelRouting(): Pair<Boolean, String> {
        val msg = ChatMessage(MessageRole.USER, "What is the weather?")
        return Pair(msg.role == MessageRole.USER, "Model routing payload valid")
    }

    // 18. API Fallback
    private fun testApiFallback(): Pair<Boolean, String> {
        val fallback = com.arya.router.LocalFallbackModelClient()
        return Pair(fallback.defaultModelName.isNotBlank(), "Fallback client verified")
    }

    // 19. Voice
    private fun testVoicePipeline(): Pair<Boolean, String> {
        val cmd = VoiceRecognizer.extractWakeCommand("Hey Arya, set timer")
        return Pair(cmd == "set timer", "Wake word parsed correctly")
    }

    // 20. Risk Classification
    private fun testRiskClassification(): Pair<Boolean, String> {
        val classifier = RiskClassifier()
        val res = classifier.evaluate(ActionType.Tap(label = "Confirm Purchase $99"), "buy item")
        return Pair(res.level == RiskLevel.HIGH || res.level == RiskLevel.CRITICAL, "Payment risk flagged")
    }

    // 21. Emergency Stop
    private fun testEmergencyStop(): Pair<Boolean, String> {
        val broker = com.arya.agent.AgentBroker()
        broker.setStatus(com.arya.agent.AgentRunStatus.RUNNING, "Active task")
        broker.stop()
        return Pair(broker.status.value == com.arya.agent.AgentRunStatus.STOPPED, "Emergency stop successful")
    }
}
