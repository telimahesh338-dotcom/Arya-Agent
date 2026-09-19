package com.arya.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStateTest {

    private fun createSampleNode(
        id: String = "test.pkg:Button#submit_btn@0",
        index: Int = 0,
        text: String? = "Submit",
        desc: String? = null,
        left: Int = 100,
        top: Int = 200,
        right: Int = 300,
        bottom: Int = 260,
        clickable: Boolean = true,
        editable: Boolean = false,
        scrollable: Boolean = false
    ): UiNode {
        return UiNode(
            id = id,
            nodeIndex = index,
            packageName = "test.pkg",
            className = "android.widget.Button",
            resourceId = "test.pkg:id/submit_btn",
            text = text,
            contentDescription = desc,
            boundsLeft = left,
            boundsTop = top,
            boundsRight = right,
            boundsBottom = bottom,
            isClickable = clickable,
            isEditable = editable,
            isScrollable = scrollable,
            isEnabled = true,
            isVisibleToUser = true
        )
    }

    @Test
    fun testUiNodeGeometry() {
        val node = createSampleNode(left = 100, top = 200, right = 300, bottom = 260)
        assertEquals(200, node.width)
        assertEquals(60, node.height)
        assertEquals(12000, node.area)
        assertEquals(200.0f, node.centerX, 0.01f)
        assertEquals(230.0f, node.centerY, 0.01f)
        assertTrue(node.contains(200f, 230f))
        assertFalse(node.contains(50f, 50f))
        assertTrue(node.isActionable)
        assertEquals("Submit", node.semanticLabel)
    }

    @Test
    fun testScreenStateFiltering() {
        val button = createSampleNode(index = 0, text = "Button 1", clickable = true)
        val input = createSampleNode(
            id = "test.pkg:EditText#search@1",
            index = 1,
            text = null,
            desc = "Search field",
            clickable = false,
            editable = true
        )
        val list = createSampleNode(
            id = "test.pkg:RecyclerView#list@2",
            index = 2,
            text = null,
            clickable = false,
            scrollable = true
        )

        val state = ScreenState(
            packageName = "test.pkg",
            activityName = "MainActivity",
            nodes = listOf(button, input, list)
        )

        assertEquals(3, state.actionableNodes.size)
        assertEquals(1, state.clickableNodes.size)
        assertEquals(1, state.editableNodes.size)
        assertEquals(1, state.scrollableNodes.size)
    }

    @Test
    fun testStructuralHashStability() {
        val nodeA = createSampleNode(index = 0, text = "Hello")
        val state1 = ScreenState(packageName = "test.pkg", nodes = listOf(nodeA))
        val state2 = ScreenState(packageName = "test.pkg", nodes = listOf(nodeA))

        assertEquals(state1.structuralHash, state2.structuralHash)

        val nodeB = createSampleNode(index = 0, text = "World")
        val state3 = ScreenState(packageName = "test.pkg", nodes = listOf(nodeB))
        assertFalse(state1.structuralHash == state3.structuralHash)
    }

    @Test
    fun testGroundingSnapping() {
        val button = createSampleNode(left = 100, top = 200, right = 300, bottom = 300)
        val state = ScreenState(packageName = "test.pkg", nodes = listOf(button))

        // Exact hit inside bounds
        val (exactHit, methodExact) = state.findGroundedNodeAt(200f, 250f)
        assertNotNull(exactHit)
        assertEquals("contained_smallest", methodExact)

        // Snapped nearby hit within 140px
        val (snappedHit, methodSnap) = state.findGroundedNodeAt(350f, 250f, maxDistance = 160f)
        assertNotNull(snappedHit)
        assertTrue(methodSnap.startsWith("snapped_nearby"))

        // Way out of reach fallback
        val (farHit, methodFar) = state.findGroundedNodeAt(900f, 900f, maxDistance = 50f)
        assertNull(farHit)
        assertEquals("raw_fallback", methodFar)
    }

    @Test
    fun testSemanticTextSearch() {
        val node1 = createSampleNode(index = 0, text = "Settings & Privacy")
        val node2 = createSampleNode(index = 1, text = null, desc = "Back to home")
        val state = ScreenState(packageName = "test.pkg", nodes = listOf(node1, node2))

        val matches1 = state.findNodesByText("privacy")
        assertEquals(1, matches1.size)
        assertEquals(node1.id, matches1.first().id)

        val matches2 = state.findNodesByText("home")
        assertEquals(1, matches2.size)
        assertEquals(node2.id, matches2.first().id)

        assertNotNull(state.findFirstNodeByText("Settings"))
        assertNull(state.findFirstNodeByText("Nonexistent"))
    }
}
