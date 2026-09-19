package com.arya.context.entity

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Clean data model representing an individual or entity relevant to the operator.
 * Preserves user privacy: strictly stores explicit operator statements without inferring sensitive attributes.
 */
@Serializable
data class PersonEntity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val nickname: String? = null,
    val relationship: String? = null, // e.g. "Brother", "Colleague", "Manager", "Friend"
    val organization: String? = null,
    val role: String? = null,
    val college: String? = null,
    val preferences: MutableList<String> = mutableListOf(),
    val notes: MutableList<String> = mutableListOf(),
    val linkedMemoryIds: MutableList<String> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var lastInteractedAt: Long = System.currentTimeMillis()
) {
    fun addPreference(pref: String) {
        if (!preferences.contains(pref)) {
            preferences.add(pref)
            lastInteractedAt = System.currentTimeMillis()
        }
    }

    fun addLinkedMemory(memoryId: String) {
        if (!linkedMemoryIds.contains(memoryId)) {
            linkedMemoryIds.add(memoryId)
            lastInteractedAt = System.currentTimeMillis()
        }
    }

    fun toContextSnippet(): String {
        val details = mutableListOf<String>()
        if (!relationship.isNullOrBlank()) details.add("Relationship: $relationship")
        if (!organization.isNullOrBlank()) details.add("Org: $organization")
        if (!role.isNullOrBlank()) details.add("Role: $role")
        if (preferences.isNotEmpty()) details.add("Preferences: ${preferences.joinToString(", ")}")
        return "$name (${details.joinToString("; ")})"
    }
}
