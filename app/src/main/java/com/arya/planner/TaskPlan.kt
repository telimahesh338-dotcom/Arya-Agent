package com.arya.planner

import com.arya.actions.ActionType
import kotlinx.serialization.Serializable

enum class SubGoalStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}

enum class PlanStatus {
    PLANNING,
    READY,
    EXECUTING,
    REPLANNING,
    COMPLETED,
    ABORTED
}

@Serializable
data class SubGoal(
    val id: String,
    val description: String,
    val targetPackage: String? = null,
    val expectedText: String? = null,
    val plannedAction: ActionType? = null,
    val isMilestone: Boolean = false,
    val isIrreversible: Boolean = false,
    var status: SubGoalStatus = SubGoalStatus.PENDING
)

@Serializable
data class TaskPlan(
    val goalId: String,
    val userGoal: String,
    val subGoals: MutableList<SubGoal> = mutableListOf(),
    var currentSubGoalIndex: Int = 0,
    var status: PlanStatus = PlanStatus.PLANNING,
    val maxReplans: Int = 5,
    var replanCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val currentSubGoal: SubGoal?
        get() = if (currentSubGoalIndex in subGoals.indices) subGoals[currentSubGoalIndex] else null

    val isComplete: Boolean
        get() = currentSubGoalIndex >= subGoals.size || status == PlanStatus.COMPLETED

    fun advanceSubGoal() {
        currentSubGoal?.status = SubGoalStatus.COMPLETED
        currentSubGoalIndex++
        if (currentSubGoalIndex >= subGoals.size) {
            status = PlanStatus.COMPLETED
        }
    }

    fun markCurrentFailed(reason: String) {
        currentSubGoal?.status = SubGoalStatus.FAILED
    }
}
