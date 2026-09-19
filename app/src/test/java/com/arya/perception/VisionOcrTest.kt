package com.arya.perception

import com.arya.perception.ocr.OcrBlock
import com.arya.perception.ocr.OcrEngine
import com.arya.perception.vision.PerceptionTier
import com.arya.perception.vision.VisualElement
import com.arya.perception.vision.VisualElementType
import com.arya.perception.vision.VisionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionOcrTest {

    @Test
    fun testOcrBlockGeometry() {
        val block = OcrBlock(
            id = "b1",
            text = "Sign In",
            boundsLeft = 100,
            boundsTop = 200,
            boundsRight = 300,
            boundsBottom = 250
        )

        assertEquals(200, block.width)
        assertEquals(50, block.height)
        assertEquals(10000, block.area)
        assertEquals(200f, block.centerX, 0.01f)
        assertEquals(225f, block.centerY, 0.01f)
        assertTrue(block.contains(200f, 225f))
        assertFalse(block.contains(50f, 50f))
    }

    @Test
    fun testOcrEngineTextSearch() {
        val engine = OcrEngine()
        val blocks = listOf(
            OcrBlock("1", "Settings and Privacy", 0, 0, 100, 50),
            OcrBlock("2", "Notifications", 0, 60, 100, 110),
            OcrBlock("3", "Privacy Policy", 0, 120, 100, 170)
        )

        val results = engine.findBlocksContaining(blocks, "Privacy")
        assertEquals(2, results.size)
        assertEquals("1", results[0].id)
        assertEquals("3", results[1].id)
    }

    @Test
    fun testVisualElementGeometry() {
        val element = VisualElement(
            id = "v1",
            type = VisualElementType.BUTTON,
            label = "Continue",
            boundsLeft = 50,
            boundsTop = 100,
            boundsRight = 250,
            boundsBottom = 160
        )

        assertEquals(200, element.width)
        assertEquals(60, element.height)
        assertEquals(12000, element.area)
        assertEquals(150f, element.centerX, 0.01f)
        assertEquals(130f, element.centerY, 0.01f)
        assertTrue(element.contains(150f, 130f))
    }

    @Test
    fun testPerceptionTierEscalationPolicy() = kotlinx.coroutines.runBlocking {
        val engine = VisionEngine()

        // 1. Screen with multiple actionable accessibility nodes -> Accessibility Only
        val node1 = UiNode("1", 0, "pkg", "android.widget.Button", boundsLeft = 0, boundsTop = 0, boundsRight = 100, boundsBottom = 50, isClickable = true)
        val node2 = UiNode("2", 1, "pkg", "android.widget.Button", boundsLeft = 0, boundsTop = 60, boundsRight = 100, boundsBottom = 110, isClickable = true)
        val stateWithNodes = ScreenState(packageName = "pkg", nodes = listOf(node1, node2))

        val res1 = engine.processPerception(stateWithNodes, screenshot = null)
        assertEquals(PerceptionTier.ACCESSIBILITY_ONLY, res1.tier)

        // 2. Empty screen without screenshot -> remains Accessibility Only
        val emptyState = ScreenState(packageName = "pkg", nodes = emptyList())
        val res2 = engine.processPerception(emptyState, screenshot = null)
        assertEquals(PerceptionTier.ACCESSIBILITY_ONLY, res2.tier)
    }
}
