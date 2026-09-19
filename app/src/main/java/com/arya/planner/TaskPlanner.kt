package com.arya.planner

import com.arya.actions.ActionType
import com.arya.actions.ScrollDirection
import com.arya.diagnostics.AryaLogger
import com.arya.perception.ChangeLevel
import com.arya.perception.ScreenDifference
import com.arya.perception.ScreenState
import java.util.UUID

/**
 * Dynamic Task Planner for ARYA.
 * Converts high-level user goals into structured, adaptive SubGoals.
 * Continuously evaluates screen state against plan expectations to trigger replanning on divergence.
 */
class TaskPlanner {

    /**
     * Decomposes a user goal into an initial structured TaskPlan.
     */
    fun createPlan(userGoal: String, initialState: ScreenState): TaskPlan {
        AryaLogger.i(TAG, "Formulating plan for goal: '$userGoal' (currentPkg=${initialState.packageName})")
        val planId = "plan_" + UUID.randomUUID().toString().take(8)
        val subGoals = mutableListOf<SubGoal>()

        val lowerGoal = userGoal.lowercase().trim()

        // 1. App Launching Intent
        val launchTarget = detectAppTarget(lowerGoal)
        if (launchTarget != null && initialState.packageName != launchTarget) {
            subGoals.add(
                SubGoal(
                    id = "sg_launch",
                    description = "Launch target application ($launchTarget)",
                    targetPackage = launchTarget,
                    plannedAction = ActionType.LaunchApp(packageName = launchTarget),
                    isMilestone = true
                )
            )
        }

        // 2. Search / Query Intent
        val searchQuery = extractSearchQuery(lowerGoal)
        if (searchQuery != null) {
            subGoals.add(
                SubGoal(
                    id = "sg_search_focus",
                    description = "Focus search field and input query '$searchQuery'",
                    plannedAction = ActionType.TypeText(text = searchQuery, pressEnter = true)
                )
            )
            subGoals.add(
                SubGoal(
                    id = "sg_search_execute",
                    description = "Execute search and await results",
                    isMilestone = true
                )
            )
        }

        // 3. Navigation / Exploration Intent
        if (lowerGoal.contains("scroll") || lowerGoal.contains("find") || lowerGoal.contains("look for")) {
            subGoals.add(
                SubGoal(
                    id = "sg_scroll_find",
                    description = "Scroll to locate desired target item",
                    plannedAction = ActionType.Scroll(direction = ScrollDirection.DOWN)
                )
            )
        }

        // 4. Form filling / interactive action
        if (lowerGoal.contains("fill") || lowerGoal.contains("register") || lowerGoal.contains("type")) {
            subGoals.add(
                SubGoal(
                    id = "sg_fill_form",
                    description = "Fill form fields with verified user inputs",
                    isMilestone = false
                )
            )
        }

        // 5. Irreversible guard / halt before submit
        if (lowerGoal.contains("stop before") || lowerGoal.contains("do not submit") || lowerGoal.contains("confirm before")) {
            subGoals.add(
                SubGoal(
                    id = "sg_guard_halt",
                    description = "Halt execution before irreversible action and request human confirmation",
                    isMilestone = true,
                    isIrreversible = true
                )
            )
        } else {
            // Final completion sub-goal
            subGoals.add(
                SubGoal(
                    id = "sg_complete",
                    description = "Verify task objective is satisfied",
                    plannedAction = ActionType.Done(summary = "Task objective achieved"),
                    isMilestone = true
                )
            )
        }

        val plan = TaskPlan(
            goalId = planId,
            userGoal = userGoal,
            subGoals = subGoals,
            status = PlanStatus.READY
        )

        AryaLogger.i(TAG, "Plan generated with ${subGoals.size} sub-goals.")
        return plan
    }

    /**
     * Evaluates if the observed screen state has diverged from expectations, requiring a Replan.
     */
    fun shouldReplan(
        currentPlan: TaskPlan,
        screenState: ScreenState,
        screenDiff: ScreenDifference?
    ): Boolean {
        if (currentPlan.isComplete || currentPlan.replanCount >= currentPlan.maxReplans) {
            return false
        }

        val subGoal = currentPlan.currentSubGoal ?: return false

        // Check 1: Unexpected app switch (unless this sub-goal is a launch action)
        if (subGoal.targetPackage != null && screenState.packageName != subGoal.targetPackage && subGoal.plannedAction !is ActionType.LaunchApp) {
            AryaLogger.w(TAG, "Divergence detected: expected pkg=${subGoal.targetPackage}, actual=${screenState.packageName}")
            return true
        }

        // Check 2: Modal dialog interception
        if (screenState.isDialogShowing && subGoal.id != "sg_dismiss_dialog") {
            AryaLogger.i(TAG, "Divergence detected: Unplanned modal dialog appeared.")
            return true
        }

        // Check 3: Action completed but zero screen change on interactive action
        if (screenDiff != null && screenDiff.changeLevel == ChangeLevel.NONE && subGoal.plannedAction is ActionType.Tap) {
            AryaLogger.w(TAG, "Divergence detected: Tap had zero effect on UI.")
            return true
        }

        return false
    }

    /**
     * Adapts an existing plan in response to dynamic UI divergence.
     */
    fun replan(
        plan: TaskPlan,
        screenState: ScreenState,
        reason: String
    ): TaskPlan {
        plan.replanCount++
        plan.status = PlanStatus.REPLANNING
        AryaLogger.i(TAG, "Replanning (attempt ${plan.replanCount}/${plan.maxReplans}): $reason")

        // If dialog appeared, inject a modal resolution sub-goal
        if (screenState.isDialogShowing) {
            val dismissGoal = SubGoal(
                id = "sg_dismiss_dialog",
                description = "Inspect and dismiss modal dialog before continuing parent task",
                isMilestone = false
            )
            plan.subGoals.add(plan.currentSubGoalIndex, dismissGoal)
        } else if (screenState.isKeyboardVisible) {
            // Soft keyboard occluding view
            val dismissKeyboardGoal = SubGoal(
                id = "sg_hide_keyboard",
                description = "Dismiss on-screen keyboard to reveal occluded UI controls",
                plannedAction = ActionType.PressBack,
                isMilestone = false
            )
            plan.subGoals.add(plan.currentSubGoalIndex, dismissKeyboardGoal)
        }

        plan.status = PlanStatus.EXECUTING
        return plan
    }

    private fun detectAppTarget(goal: String): String? {
        return when {
            goal.contains("chrome") || goal.contains("browser") -> "com.android.chrome"
            goal.contains("setting") -> "com.android.settings"
            goal.contains("youtube") -> "com.google.android.youtube"
            goal.contains("maps") -> "com.google.android.apps.maps"
            goal.contains("calculator") -> "com.google.android.calculator"
            goal.contains("camera") -> "com.google.android.GoogleCamera"
            else -> null
        }
    }

    private fun extractSearchQuery(goal: String): String? {
        val keywords = listOf("search for ", "find ", "look for ", "search ")
        for (kw in keywords) {
            if (goal.contains(kw)) {
                val query = goal.substringAfter(kw).substringBefore(" and ").substringBefore(" then ").substringBefore(" in ")
                if (query.isNotBlank()) return query.trim()
            }
        }
        return null
    }

    companion object {
        private const val TAG = "TaskPlanner"
    }
}
