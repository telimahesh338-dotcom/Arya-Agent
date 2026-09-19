package com.arya.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenChangeDetectorTest {

    private val detector = ScreenChangeDetector()

    private fun createNode(index: Int, text: String): UiNode {
        return UiNode(
            id = "pkg:View#id$index@$index",
            nodeIndex = index,
            packageName = "pkg",
            className = "android.widget.TextView",
            text = text,
            boundsLeft = 0,
            boundsTop = index * 100,
            boundsRight = 500,
            boundsBottom = (index + 1) * 100,
            isClickable = true,
            isEnabled = true,
            isVisibleToUser = true
        )
    }

    @Test
    fun testInitialObservation() {
        val state = ScreenState(packageName = "com.test.app", nodes = listOf(createNode(0, "Item 1")))
        val diff = detector.detectChange(null, state)

        assertEquals(ChangeLevel.SIGNIFICANT, diff.changeLevel)
        assertTrue(diff.packageChanged)
        assertEquals(1, diff.addedNodeCount)
    }

    @Test
    fun testNoChange() {
        val state1 = ScreenState(packageName = "com.test.app", nodes = listOf(createNode(0, "Item 1")))
        val state2 = ScreenState(packageName = "com.test.app", nodes = listOf(createNode(0, "Item 1")))

        val diff = detector.detectChange(state1, state2)
        assertEquals(ChangeLevel.NONE, diff.changeLevel)
        assertFalse(diff.packageChanged)
        assertFalse(diff.dialogAppeared)
        assertFalse(diff.keyboardToggled)
    }

    @Test
    fun testPackageSwitch() {
        val state1 = ScreenState(packageName = "com.app.a", nodes = listOf(createNode(0, "Item 1")))
        val state2 = ScreenState(packageName = "com.app.b", nodes = listOf(createNode(0, "Item 1")))

        val diff = detector.detectChange(state1, state2)
        assertEquals(ChangeLevel.SIGNIFICANT, diff.changeLevel)
        assertTrue(diff.packageChanged)
    }

    @Test
    fun testDialogAppearanceAndDismissal() {
        val baseState = ScreenState(packageName = "com.test.app", isDialogShowing = false)
        val dialogState = ScreenState(packageName = "com.test.app", isDialogShowing = true)

        val appeared = detector.detectChange(baseState, dialogState)
        assertEquals(ChangeLevel.SIGNIFICANT, appeared.changeLevel)
        assertTrue(appeared.dialogAppeared)
        assertFalse(appeared.dialogDismissed)

        val dismissed = detector.detectChange(dialogState, baseState)
        assertEquals(ChangeLevel.SIGNIFICANT, dismissed.changeLevel)
        assertTrue(dismissed.dialogDismissed)
        assertFalse(dismissed.dialogAppeared)
    }

    @Test
    fun testKeyboardToggle() {
        val keyboardHidden = ScreenState(packageName = "com.test.app", isKeyboardVisible = false)
        val keyboardShown = ScreenState(packageName = "com.test.app", isKeyboardVisible = true)

        val diff = detector.detectChange(keyboardHidden, keyboardShown)
        assertEquals(ChangeLevel.SIGNIFICANT, diff.changeLevel)
        assertTrue(diff.keyboardToggled)
    }
}
