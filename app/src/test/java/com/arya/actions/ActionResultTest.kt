package com.arya.actions

import com.arya.grounding.GroundedTarget
import com.arya.grounding.GroundingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionResultTest {

    @Test
    fun testActionResultProperties() {
        val target = GroundedTarget(
            stableId = "btn_1",
            source = GroundingSource.ACCESSIBILITY_NODE,
            confidence = 1.0f,
            screenX = 100f,
            screenY = 200f,
            boundsLeft = 50,
            boundsTop = 150,
            boundsRight = 150,
            boundsBottom = 250,
            semanticLabel = "Confirm"
        )

        val result = ActionResult(
            action = ActionType.Tap(nodeId = "btn_1"),
            success = true,
            target = target,
            executionTimeMs = 85L,
            previousStateHash = "hash_before",
            resultingStateHash = "hash_after",
            differenceSummary = "Modal dismissed"
        )

        assertTrue(result.success)
        assertEquals("btn_1", result.target?.stableId)
        assertEquals(85L, result.executionTimeMs)
        assertEquals("hash_before", result.previousStateHash)
        assertEquals("hash_after", result.resultingStateHash)
        assertFalse(result.isRetryable)
    }

    @Test
    fun testFailedActionResultIsRetryable() {
        val failedResult = ActionResult(
            action = ActionType.Tap(x = 100f, y = 200f),
            success = false,
            failureReason = "Target not visible",
            isRetryable = true
        )

        assertFalse(failedResult.success)
        assertTrue(failedResult.isRetryable)
        assertNotNull(failedResult.failureReason)
    }

    @Test
    fun testNewActionTypes() {
        val drag = ActionType.Drag(100f, 200f, 100f, 800f, durationMs = 700L)
        assertEquals(700L, drag.durationMs)

        val clear = ActionType.ClearText(nodeId = "input_name")
        assertTrue(clear.hasSemanticTarget)

        val clearCoord = ActionType.ClearText(targetX = 200f, targetY = 300f)
        assertFalse(clearCoord.hasSemanticTarget)

        val retry = ActionType.Retry(originalAction = clear, attemptNumber = 2)
        assertEquals(2, retry.attemptNumber)

        val cancel = ActionType.Cancel(reason = "Emergency Stop")
        assertEquals("Emergency Stop", cancel.reason)
    }
}
