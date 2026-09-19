package com.arya.reflection

import com.arya.actions.ActionType
import com.arya.perception.ScreenState
import com.arya.perception.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionEngineTest {

    private val engine = ReflectionEngine()

    private fun createNode(id: String, text: String): UiNode {
        return UiNode(
            id = id,
            nodeIndex = 0,
            packageName = "com.test",
            className = "android.widget.TextView",
            text = text,
            boundsLeft = 0,
            boundsTop = 0,
            boundsRight = 200,
            boundsBottom = 100,
            isClickable = true
        )
    }

    @Test
    fun testExpectedEffectVerified() {
        val before = ScreenState(packageName = "com.test", nodes = listOf(createNode("1", "Cart Empty")))
        val after = ScreenState(packageName = "com.test", nodes = listOf(createNode("2", "Checkout - 1 Item")))

        val record = engine.reflect(
            previousState = before,
            action = ActionType.Tap(x = 100f, y = 50f),
            currentState = after,
            expectedEffect = "Checkout"
        )

        assertEquals(ReflectionVerdict.SUCCESS, record.verdict)
        assertEquals(RecommendedNextStep.PROCEED, record.nextStep)
        assertEquals(0, record.consecutiveFailures)
    }

    @Test
    fun testWrongTargetZeroChange() {
        val node = createNode("1", "Dead Area")
        val state = ScreenState(packageName = "com.test", nodes = listOf(node))

        val record = engine.reflect(
            previousState = state,
            action = ActionType.Tap(x = 500f, y = 500f),
            currentState = state
        )

        assertEquals(ReflectionVerdict.WRONG_TARGET, record.verdict)
        assertEquals(RecommendedNextStep.RETRY_WITH_JITTER, record.nextStep)
    }

    @Test
    fun testPopupEncountered() {
        val before = ScreenState(packageName = "com.test", isDialogShowing = false)
        val after = ScreenState(packageName = "com.test", isDialogShowing = true)

        val record = engine.reflect(
            previousState = before,
            action = ActionType.Tap(x = 100f, y = 100f),
            currentState = after
        )

        assertEquals(ReflectionVerdict.POPUP_ENCOUNTERED, record.verdict)
        assertEquals(RecommendedNextStep.DISMISS_POPUP, record.nextStep)
    }

    @Test
    fun testStuckLoopDetection() {
        val loopEngine = ReflectionEngine()
        val node = createNode("1", "Static Screen")
        val state = ScreenState(packageName = "com.test", nodes = listOf(node))

        // First tap
        loopEngine.reflect(state, ActionType.Tap(x = 10f, y = 10f), state)
        // Second tap
        loopEngine.reflect(state, ActionType.Tap(x = 10f, y = 10f), state)
        // Third tap -> should trigger stuck loop
        val thirdRecord = loopEngine.reflect(state, ActionType.Tap(x = 10f, y = 10f), state)

        assertTrue(thirdRecord.isLoopDetected)
        assertEquals(RecommendedNextStep.RECOVER_BACKSTEP, thirdRecord.nextStep)
    }

    @Test
    fun testAppCrashDetection() {
        val before = ScreenState(packageName = "com.my.app")
        val after = ScreenState(packageName = "com.android.launcher")

        val record = engine.reflect(
            previousState = before,
            action = ActionType.Tap(x = 100f, y = 100f),
            currentState = after
        )

        assertEquals(ReflectionVerdict.APP_CRASH, record.verdict)
        assertEquals(RecommendedNextStep.REPLAN, record.nextStep)
    }
}
