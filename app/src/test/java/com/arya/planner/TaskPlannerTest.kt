package com.arya.planner

import com.arya.actions.ActionType
import com.arya.perception.ChangeLevel
import com.arya.perception.ScreenDifference
import com.arya.perception.ScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPlannerTest {

    private val planner = TaskPlanner()

    @Test
    fun testCreatePlanForBrowserSearch() {
        val initialState = ScreenState(packageName = "com.android.launcher")
        val goal = "Open Chrome, search for OpenAI, and stop before submitting anything."

        val plan = planner.createPlan(goal, initialState)
        assertEquals(PlanStatus.READY, plan.status)
        assertTrue(plan.subGoals.isNotEmpty())

        // Subgoal 1: Launch Chrome
        val firstGoal = plan.subGoals[0]
        assertEquals("com.android.chrome", firstGoal.targetPackage)
        assertTrue(firstGoal.plannedAction is ActionType.LaunchApp)

        // Subgoal: Guardrail to halt before irreversible submit
        val hasHaltGoal = plan.subGoals.any { it.isIrreversible }
        assertTrue("Must inject irreversible guardrail sub-goal", hasHaltGoal)
    }

    @Test
    fun testPlanAdvancement() {
        val initialState = ScreenState(packageName = "com.test")
        val plan = planner.createPlan("Launch calculator", initialState)

        val totalGoals = plan.subGoals.size
        assertEquals(0, plan.currentSubGoalIndex)

        for (i in 0 until totalGoals) {
            assertFalse(plan.isComplete)
            plan.advanceSubGoal()
        }

        assertTrue(plan.isComplete)
        assertEquals(PlanStatus.COMPLETED, plan.status)
    }

    @Test
    fun testDivergenceDetectionOnDialog() {
        val initialState = ScreenState(packageName = "com.test", isDialogShowing = false)
        val plan = planner.createPlan("Open settings", initialState)

        val dialogState = ScreenState(packageName = "com.test", isDialogShowing = true)
        val diff = ScreenDifference(
            changeLevel = ChangeLevel.SIGNIFICANT,
            packageChanged = false,
            dialogAppeared = true,
            dialogDismissed = false,
            keyboardToggled = false,
            addedNodeCount = 2,
            removedNodeCount = 0,
            summary = "Dialog appeared"
        )

        val needReplan = planner.shouldReplan(plan, dialogState, diff)
        assertTrue("Must trigger replan when unexpected dialog appears", needReplan)

        // Replan should inject dialog dismiss sub-goal at current index
        val replanned = planner.replan(plan, dialogState, "Unexpected modal dialog")
        assertEquals(1, replanned.replanCount)
        assertEquals("sg_dismiss_dialog", replanned.currentSubGoal?.id)
    }
}
