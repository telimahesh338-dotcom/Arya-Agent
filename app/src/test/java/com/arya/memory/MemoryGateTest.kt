package com.arya.memory

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGateTest {

    private val gate = MemoryGate()
    private val store = MemoryStore(gate)

    @Test
    fun testDiscardTrivialEphemeral() {
        val decision = gate.evaluate("Today I drank coffee.")
        assertFalse("Trivial chit-chat must be discarded", decision.shouldStore)
        assertTrue(decision.importanceScore < 0.5f)
    }

    @Test
    fun testRejectSensitiveCredentials() {
        val decision = gate.evaluate("My account password is SuperSecretPassword123")
        assertFalse("Passwords must be rejected from memory", decision.shouldStore)
        assertEquals("[REDACTED]", decision.sanitizedContent)
    }

    @Test
    fun testAdmitPreferenceMemory() {
        val decision = gate.evaluate("Always use DuckDuckGo for searches.")
        assertTrue(decision.shouldStore)
        assertEquals(MemoryType.PREFERENCE, decision.type)
        assertTrue(decision.importanceScore >= 0.85f)
    }

    @Test
    fun testAdmitPeopleEntityMemory() {
        val decision = gate.evaluate("My brother's name is Rahul.")
        assertTrue(decision.shouldStore)
        assertEquals(MemoryType.PEOPLE, decision.type)
        assertTrue(decision.importanceScore >= 0.80f)
    }

    @Test
    fun testAdmitTemporalEventWithExpiration() {
        val decision = gate.evaluate("I have a meeting tomorrow at 5 PM.")
        assertTrue(decision.shouldStore)
        assertEquals(MemoryType.EPISODIC, decision.type)
        assertNotNull(decision.expirationMs)
        assertTrue(decision.expirationMs!! > System.currentTimeMillis())
    }

    @Test
    fun testStoreQueryAndForget() = runBlocking {
        store.processAndStore("My brother's name is Rahul.")
        store.processAndStore("I prefer Kannada language.")
        store.processAndStore("Today I drank tea.") // discarded

        val people = store.query("Rahul", typeFilter = MemoryType.PEOPLE)
        assertEquals(1, people.size)
        assertEquals("My brother's name is Rahul.", people[0].content)

        // Verify forget
        val id = people[0].id
        val forgotten = store.forget(id)
        assertTrue(forgotten)

        val queryAfter = store.query("Rahul")
        assertEquals(0, queryAfter.size)
    }

    @Test
    fun testDecayAndEviction() = runBlocking {
        val item = store.processAndStore("Remember that server IP is 192.168.1.100")
        assertNotNull(item)

        // Apply decay over 20 inactive weeks
        val evicted = store.decayAndEvict(weeksPassed = 20.0f)
        assertTrue(evicted >= 1)
    }
}
