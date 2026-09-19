package com.arya.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AryaLoggerTest {

    @Test
    fun testOpenAiKeyRedaction() {
        val raw = "Authorization: Bearer sk-1234567890abcdef1234567890abcdef"
        val sanitized = AryaLogger.sanitize(raw)
        assertFalse(sanitized.contains("sk-1234567890abcdef1234567890abcdef"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun testGeminiKeyRedaction() {
        val raw = "https://generativelanguage.googleapis.com/v1beta?key=AIzaSyA1234567890123456789012345678901"
        val sanitized = AryaLogger.sanitize(raw)
        assertFalse(sanitized.contains("AIzaSyA1234567890123456789012345678901"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun testPasswordRedaction() {
        val raw = "user credentials: password='SuperSecretPassword123' and username='alice'"
        val sanitized = AryaLogger.sanitize(raw)
        assertFalse(sanitized.contains("SuperSecretPassword123"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun testCreditCardRedaction() {
        val raw = "Payment card: 4111 2222 3333 4444 on file"
        val sanitized = AryaLogger.sanitize(raw)
        assertFalse(sanitized.contains("4111 2222 3333 4444"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun testNormalLogPreservation() {
        val raw = "Window state changed: pkg=com.android.settings, cls=SubSettings"
        val sanitized = AryaLogger.sanitize(raw)
        assertTrue(sanitized == raw)
    }
}
