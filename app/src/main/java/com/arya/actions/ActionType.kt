package com.arya.actions

import com.arya.security.RiskLevel
import kotlinx.serialization.Serializable

enum class ScrollDirection {
    UP, DOWN, LEFT, RIGHT
}

/**
 * Sealed hierarchy of all discrete actions executable by ARYA.
 * Supports both Semantic Node Targeting (preferred) and Coordinate Fallback.
 */
@Serializable
sealed class ActionType {

    @Serializable
    data class Tap(
        val nodeId: String? = null,
        val targetText: String? = null,
        val legendId: Int? = null,
        val x: Float = 0f,
        val y: Float = 0f,
        val label: String? = null
    ) : ActionType() {
        val hasSemanticTarget: Boolean
            get() = !nodeId.isNullOrBlank() || !targetText.isNullOrBlank() || legendId != null
    }

    @Serializable
    data class LongTap(
        val nodeId: String? = null,
        val targetText: String? = null,
        val x: Float = 0f,
        val y: Float = 0f,
        val durationMs: Long = 800L
    ) : ActionType() {
        val hasSemanticTarget: Boolean
            get() = !nodeId.isNullOrBlank() || !targetText.isNullOrBlank()
    }

    @Serializable
    data class Swipe(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val durationMs: Long = 350L
    ) : ActionType()

    @Serializable
    data class Scroll(
        val direction: ScrollDirection = ScrollDirection.DOWN,
        val nodeId: String? = null,
        val distanceFraction: Float = 0.5f
    ) : ActionType()

    @Serializable
    data class TypeText(
        val text: String,
        val nodeId: String? = null,
        val targetText: String? = null,
        val targetX: Float? = null,
        val targetY: Float? = null,
        val pressEnter: Boolean = false
    ) : ActionType() {
        val hasSemanticTarget: Boolean
            get() = !nodeId.isNullOrBlank() || !targetText.isNullOrBlank()
    }

    @Serializable
    data object PressBack : ActionType()

    @Serializable
    data object PressHome : ActionType()

    @Serializable
    data class LaunchApp(
        val packageName: String
    ) : ActionType()

    @Serializable
    data class Wait(
        val seconds: Double = 2.0
    ) : ActionType()

    @Serializable
    data class Confirm(
        val riskLevel: RiskLevel,
        val prompt: String
    ) : ActionType()

    @Serializable
    data class TakeOver(
        val reason: String
    ) : ActionType()

    @Serializable
    data class Done(
        val summary: String
    ) : ActionType()

    @Serializable
    data class Fail(
        val reason: String
    ) : ActionType()
}
