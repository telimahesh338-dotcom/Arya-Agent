package com.arya.recovery

import com.arya.actions.ActionType
import com.arya.perception.ScreenState
import com.arya.perception.UiNode
import com.arya.reflection.RecommendedNextStep
import com.arya.reflection.ReflectionRecord
import com.arya.reflection.ReflectionVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryEngineTest {

    private val engine = RecoveryEngine(maxRetriesPerAction = 2, maxBacksteps = 2)

    @Test
    fun testDeadTapRecoveryWithJitter() {
        val state = ScreenState(packageName = "com.test")
        val reflection = ReflectionRecord(
            verdict = ReflectionVerdict.WRONG_TARGET,
            nextStep = RecommendedNextStep.RETRY_WITH_JITTER,
            confidence = 0.8f,
            rationale = "Tap produced 0% screen change"
        )

        val recovery = engine.planRecovery(
            reflection = reflection,
            failedAction = ActionType.Tap(x = 100f, y = 200f),
            currentState = state
        )

        assertEquals(RecoveryType.RETRY_WITH_COORDINATE_JITTER, recovery.type)
        assertTrue(recovery.actionToExecute is ActionType.Tap)
    }

    @Test
    fun testPopupDismissalAutoButton() {
        val allowNode = UiNode(
            id = "btn_allow",
            nodeIndex = 0,
            packageName = "com.android.permissioncontroller",
            className = "android.widget.Button",
            text = "While using the app",
            boundsLeft = 100,
            boundsTop = 500,
            boundsRight = 400,
            boundsBottom = 580,
            isClickable = true
        )
        val dialogState = ScreenState(packageName = "com.android.permissioncontroller", isDialogShowing = true, nodes = listOf(allowNode))

        val reflection = ReflectionRecord(
            verdict = ReflectionVerdict.POPUP_ENCOUNTERED,
            nextStep = RecommendedNextStep.DISMISS_POPUP,
            confidence = 0.9f,
            rationale = "Permission dialog appeared"
        )

        val recovery = engine.planRecovery(
            reflection = reflection,
            failedAction = ActionType.Tap(x = 10f, y = 10f),
            currentState = dialogState
        )

        assertEquals(RecoveryType.DISMISS_MODAL_OR_PERMISSION, recovery.type)
        assertTrue(recovery.actionToExecute is ActionType.Tap)
        val tapAction = recovery.actionToExecute as ActionType.Tap
        assertEquals("btn_allow", tapAction.nodeId)
    }

    @Test
    fun testStuckLoopBackstepAndEscalation() {
        val state = ScreenState(packageName = "com.test")
        val loopReflection = ReflectionRecord(
            verdict = ReflectionVerdict.FAILURE,
            nextStep = RecommendedNextStep.RECOVER_BACKSTEP,
            confidence = 0.95f,
            rationale = "Stuck loop",
            isLoopDetected = true
        )

        // 1st backstep
        val rec1 = engine.planRecovery(loopReflection, ActionType.Tap(x = 10f, y = 10f), state)
        assertEquals(RecoveryType.BACKSTEP_NAVIGATION, rec1.type)
        assertTrue(rec1.actionToExecute is ActionType.PressBack)

        // 2nd backstep
        val rec2 = engine.planRecovery(loopReflection, ActionType.Tap(x = 10f, y = 10f), state)
        assertEquals(RecoveryType.BACKSTEP_NAVIGATION, rec2.type)

        // 3rd time -> budget exhausted -> escalate to Human Takeover
        val rec3 = engine.planRecovery(loopReflection, ActionType.Tap(x = 10f, y = 10f), state)
        assertEquals(RecoveryType.REQUEST_HUMAN_INTERVENTION, rec3.type)
        assertTrue(rec3.actionToExecute is ActionType.TakeOver)
    }

    @Test
    fun testAppCrashRelaunch() {
        val launcherState = ScreenState(packageName = "com.android.launcher")
        val crashReflection = ReflectionRecord(
            verdict = ReflectionVerdict.APP_CRASH,
            nextStep = RecommendedNextStep.REPLAN,
            confidence = 0.9f,
            rationale = "App crashed to launcher"
        )

        val recovery = engine.planRecovery(
            reflection = crashReflection,
            failedAction = ActionType.Tap(x = 100f, y = 100f),
            currentState = launcherState,
            targetPackage = "com.target.app"
        )

        assertEquals(RecoveryType.RELAUNCH_CRASHED_APP, recovery.type)
        assertTrue(recovery.actionToExecute is ActionType.LaunchApp)
        val launch = recovery.actionToExecute as ActionType.LaunchApp
        assertEquals("com.target.app", launch.packageName)
    }
}
