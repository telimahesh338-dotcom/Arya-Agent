package com.arya.grounding

import com.arya.perception.ScreenState
import com.arya.perception.UiNode
import com.arya.perception.ocr.OcrBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingEngineTest {

    private val engine = GroundingEngine()

    private fun createNode(
        id: String,
        text: String?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        clickable: Boolean = true
    ): UiNode {
        return UiNode(
            id = id,
            nodeIndex = 0,
            packageName = "com.test",
            className = "android.widget.Button",
            text = text,
            boundsLeft = left,
            boundsTop = top,
            boundsRight = right,
            boundsBottom = bottom,
            isClickable = clickable,
            isEnabled = true,
            isVisibleToUser = true
        )
    }

    @Test
    fun testPriority1DirectNodeId() {
        val node = createNode("btn_login", "Log In", 100, 200, 300, 260)
        val state = ScreenState(packageName = "com.test", nodes = listOf(node))

        val grounded = engine.groundTarget(state, targetNodeId = "btn_login")
        assertEquals(GroundingSource.ACCESSIBILITY_NODE, grounded.source)
        assertEquals(1.0f, grounded.confidence, 0.01f)
        assertEquals(200f, grounded.screenX, 0.01f)
        assertEquals(230f, grounded.screenY, 0.01f)
        assertTrue(engine.isTargetValid(grounded, state))
    }

    @Test
    fun testPriority2TextMatch() {
        val node = createNode("btn_submit", "Submit Order", 100, 500, 400, 580)
        val state = ScreenState(packageName = "com.test", nodes = listOf(node))

        val grounded = engine.groundTarget(state, targetText = "submit")
        assertEquals(GroundingSource.TEXT_DESCRIPTION, grounded.source)
        assertEquals(0.95f, grounded.confidence, 0.01f)
        assertEquals("Submit Order", grounded.semanticLabel)
    }

    @Test
    fun testPriority4OcrMatch() {
        val state = ScreenState(packageName = "com.test", nodes = emptyList())
        val ocrBlock = OcrBlock("ocr_1", "Checkout Now", 50, 100, 250, 160)

        val grounded = engine.groundTarget(state, targetText = "checkout", ocrBlocks = listOf(ocrBlock))
        assertEquals(GroundingSource.OCR_BOUNDS, grounded.source)
        assertEquals(150f, grounded.screenX, 0.01f)
        assertEquals(130f, grounded.screenY, 0.01f)
    }

    @Test
    fun testPriority5TaprootSnapping() {
        val node = createNode("card_item", "Product", 100, 200, 500, 400)
        val state = ScreenState(packageName = "com.test", nodes = listOf(node))

        // Coordinate inside bounding box
        val groundedContained = engine.groundTarget(state, predictedX = 250f, predictedY = 300f)
        assertEquals(GroundingSource.VISION_SNAPPING, groundedContained.source)
        assertEquals(300f, groundedContained.screenX, 0.01f)
        assertEquals(300f, groundedContained.screenY, 0.01f)

        // Coordinate outside but within 140px snap radius (e.g. 520, 300 is 20px right of bounds)
        val groundedNearby = engine.groundTarget(state, predictedX = 520f, predictedY = 300f)
        assertEquals(GroundingSource.VISION_SNAPPING, groundedNearby.source)
    }

    @Test
    fun testPriority6ViewportClampedFallback() {
        val state = ScreenState(packageName = "com.test", screenWidth = 1080, screenHeight = 2400, nodes = emptyList())

        // Out-of-bounds coordinate
        val grounded = engine.groundTarget(state, predictedX = 1200f, predictedY = 3000f)
        assertEquals(GroundingSource.COORDINATE_FALLBACK, grounded.source)
        assertEquals(1080f, grounded.screenX, 0.01f)
        assertEquals(2400f, grounded.screenY, 0.01f)
        assertFalse(grounded.isActionable)
    }
}
