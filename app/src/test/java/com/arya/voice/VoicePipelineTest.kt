package com.arya.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePipelineTest {

    @Test
    fun testWakeCommandExtraction() {
        val cmd1 = VoiceRecognizer.extractWakeCommand("Hey Arya, open Chrome browser")
        assertEquals("open Chrome browser", cmd1)

        val cmd2 = VoiceRecognizer.extractWakeCommand("Arya find hotels in Bangalore")
        assertEquals("find hotels in Bangalore", cmd2)

        val cmd3 = VoiceRecognizer.extractWakeCommand("Ok Arya, call Rahul")
        assertEquals("call Rahul", cmd3)

        // No wake prefix
        val cmd4 = VoiceRecognizer.extractWakeCommand("Just a normal sentence")
        assertNull(cmd4)
    }

    @Test
    fun testSpeechRecognitionResultModel() {
        val result = SpeechRecognitionResult(
            transcript = "Search for flights to Delhi",
            isFinal = true,
            confidence = 0.98f
        )

        assertEquals("Search for flights to Delhi", result.transcript)
        assertEquals(true, result.isFinal)
        assertEquals(0.98f, result.confidence, 0.01f)
    }
}
