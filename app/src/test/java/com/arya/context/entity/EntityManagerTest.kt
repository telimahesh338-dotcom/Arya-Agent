package com.arya.context.entity

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityManagerTest {

    private val manager = EntityManager()

    @Test
    fun testUpsertAndFindPerson() = runBlocking {
        val person = manager.upsertPerson(
            name = "Rahul Sharma",
            nickname = "Rahul",
            relationship = "Brother",
            organization = "Infosys",
            role = "Software Engineer"
        )

        assertNotNull(person)
        assertEquals("Rahul Sharma", person.name)
        assertEquals("Brother", person.relationship)

        // Find by nickname
        val found = manager.findPerson("rahul")
        assertNotNull(found)
        assertEquals("Rahul Sharma", found?.name)
    }

    @Test
    fun testLinkedMemoryAndContextExtraction() = runBlocking {
        val person = manager.upsertPerson(
            name = "Priya Rao",
            relationship = "Colleague",
            organization = "Google"
        )

        manager.linkMemory("Priya Rao", "mem_meeting_priya_101")
        assertEquals(1, person.linkedMemoryIds.size)

        // Context string match
        val context = manager.getRelevantContext("Can you schedule a meeting with Priya Rao tomorrow?")
        assertTrue(context.contains("Priya Rao"))
        assertTrue(context.contains("Colleague"))
        assertTrue(context.contains("Google"))
    }

    @Test
    fun testDeleteEntity() = runBlocking {
        val person = manager.upsertPerson("John Doe")
        val deleted = manager.deleteEntity(person.id)
        assertTrue(deleted)

        val foundAfter = manager.findPerson("John Doe")
        assertTrue(foundAfter == null)
    }
}
