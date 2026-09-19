package com.arya.context.entity

import com.arya.diagnostics.AryaLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages operator social and entity context.
 * Enables ARYA to understand references to people, colleagues, and organizations.
 */
class EntityManager {

    private val mutex = Mutex()
    private val entities = mutableListOf<PersonEntity>()

    suspend fun upsertPerson(
        name: String,
        nickname: String? = null,
        relationship: String? = null,
        organization: String? = null,
        role: String? = null,
        college: String? = null
    ): PersonEntity = mutex.withLock {
        val cleanName = name.trim()
        val existing = entities.firstOrNull { it.name.equals(cleanName, ignoreCase = true) || (nickname != null && it.nickname.equals(nickname, ignoreCase = true)) }

        if (existing != null) {
            val updated = existing.copy(
                nickname = nickname ?: existing.nickname,
                relationship = relationship ?: existing.relationship,
                organization = organization ?: existing.organization,
                role = role ?: existing.role,
                college = college ?: existing.college,
                lastInteractedAt = System.currentTimeMillis()
            )
            entities[entities.indexOf(existing)] = updated
            AryaLogger.i(TAG, "Updated existing person entity: ${updated.name}")
            return@withLock updated
        }

        val newPerson = PersonEntity(
            name = cleanName,
            nickname = nickname?.trim(),
            relationship = relationship?.trim(),
            organization = organization?.trim(),
            role = role?.trim(),
            college = college?.trim()
        )
        entities.add(newPerson)
        AryaLogger.i(TAG, "Registered new person entity: ${newPerson.name} (${newPerson.relationship})")
        return@withLock newPerson
    }

    suspend fun findPerson(query: String): PersonEntity? = mutex.withLock {
        val q = query.trim().lowercase()
        entities.firstOrNull {
            it.name.lowercase().contains(q) || (it.nickname?.lowercase()?.contains(q) == true)
        }
    }

    suspend fun linkMemory(personName: String, memoryId: String): Boolean = mutex.withLock {
        val person = entities.firstOrNull { it.name.equals(personName, ignoreCase = true) } ?: return@withLock false
        person.addLinkedMemory(memoryId)
        AryaLogger.d(TAG, "Linked memory '$memoryId' to person '${person.name}'")
        true
    }

    suspend fun deleteEntity(id: String): Boolean = mutex.withLock {
        val removed = entities.removeAll { it.id == id }
        AryaLogger.i(TAG, "Deleted person entity id '$id': $removed")
        removed
    }

    suspend fun getRelevantContext(text: String): String = mutex.withLock {
        val lower = text.lowercase()
        val matched = entities.filter {
            lower.contains(it.name.lowercase()) || (it.nickname != null && lower.contains(it.nickname.lowercase()))
        }

        if (matched.isEmpty()) return@withLock ""
        return@withLock "Relevant People/Entity Context:\n" + matched.joinToString("\n") { "• ${it.toContextSnippet()}" }
    }

    suspend fun getAll(): List<PersonEntity> = mutex.withLock {
        entities.toList()
    }

    companion object {
        private const val TAG = "EntityManager"
    }
}
