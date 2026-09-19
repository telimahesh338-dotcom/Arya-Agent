package com.arya.actions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionTypeTest {

    @Test
    fun testSemanticTapTargeting() {
        val semanticTap = ActionType.Tap(nodeId = "com.app:Button#ok@1")
        assertTrue(semanticTap.hasSemanticTarget)

        val textTap = ActionType.Tap(targetText = "Submit")
        assertTrue(textTap.hasSemanticTarget)

        val legendTap = ActionType.Tap(legendId = 3)
        assertTrue(legendTap.hasSemanticTarget)

        val coordTap = ActionType.Tap(x = 540f, y = 1200f)
        assertFalse(coordTap.hasSemanticTarget)
    }

    @Test
    fun testSemanticLongTapTargeting() {
        val semanticLongTap = ActionType.LongTap(nodeId = "com.app:Item#row@5")
        assertTrue(semanticLongTap.hasSemanticTarget)

        val coordLongTap = ActionType.LongTap(x = 100f, y = 200f)
        assertFalse(coordLongTap.hasSemanticTarget)
    }

    @Test
    fun testSemanticTypeTextTargeting() {
        val semanticType = ActionType.TypeText(text = "Hello", nodeId = "com.app:EditText#input@0")
        assertTrue(semanticType.hasSemanticTarget)

        val coordType = ActionType.TypeText(text = "Hello", targetX = 300f, targetY = 400f)
        assertFalse(coordType.hasSemanticTarget)
    }
}
