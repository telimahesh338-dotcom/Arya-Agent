package com.arya.memory

import com.arya.diagnostics.AryaLogger
import java.util.regex.Pattern

data class MemoryGateDecision(
    val shouldStore: Boolean,
    val type: MemoryType,
    val importanceScore: Float,
    val expirationMs: Long? = null,
    val rationale: String,
    val sanitizedContent: String
)

/**
 * Gatekeeper enforcing ARYA's core memory principle:
 * UNDERSTAND EVERYTHING. REMEMBER ONLY WHAT MATTERS.
 *
 * Filters ephemeral chit-chat, extracts high-value preferences and entities,
 * blocks sensitive credentials, and attaches decay/expiration parameters.
 */
class MemoryGate {

    private val sensitivePatterns = listOf(
        Pattern.compile("(password|passwd|pin|secret|cvv)\\s*[:=]?\\s*['\"]?([^'\"\\s]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:\\d{4}[ -]?){3}\\d{4}\\b") // Credit cards
    )

    private val preferenceMarkers = listOf(
        "i prefer", "always use", "never use", "my favorite", "i like", "i dislike", "i hate",
        "prefer dark mode", "prefer light mode", "call me"
    )

    private val peopleMarkers = listOf(
        "my brother", "my sister", "my mother", "my father", "my mom", "my dad",
        "my friend", "my boss", "my wife", "my husband", "my son", "my daughter",
        "colleague", "named", "birthday is"
    )

    private val temporalMeetingMarkers = listOf(
        "meeting tomorrow", "flight tomorrow", "appointment at", "reminder for", "call at", "due at"
    )

    private val ephemeralTrivialMarkers = listOf(
        "i drank", "i ate", "today i had", "it is hot", "weather is", "hello", "hi arya", "good morning", "good night"
    )

    /**
     * Evaluates whether a statement should be retained in long-term memory.
     */
    fun evaluate(statement: String, source: String = "user_dialog"): MemoryGateDecision {
        val lower = statement.lowercase().trim()

        // 1. Security Check: Never store raw passwords, PINs, or credit cards
        for (pattern in sensitivePatterns) {
            if (pattern.matcher(statement).find()) {
                AryaLogger.w(TAG, "Memory candidate rejected: contains sensitive credential pattern.")
                return MemoryGateDecision(
                    shouldStore = false,
                    type = MemoryType.WORKING,
                    importanceScore = 0.0f,
                    rationale = "Rejected: Contains sensitive credential or payment card pattern.",
                    sanitizedContent = "[REDACTED]"
                )
            }
        }

        // 2. External / Transient Source Check
        if (source == "external_search" || source == "web_tool" || source == "gemini_transient") {
            return MemoryGateDecision(
                shouldStore = false,
                type = MemoryType.WORKING,
                importanceScore = 0.2f,
                rationale = "External web/search knowledge belongs in Working Memory only.",
                sanitizedContent = statement
            )
        }

        // 3. Trivial / Ephemeral Filter
        if (ephemeralTrivialMarkers.any { lower.contains(it) }) {
            AryaLogger.d(TAG, "Memory candidate discarded: ephemeral trivial statement ('$statement')")
            return MemoryGateDecision(
                shouldStore = false,
                type = MemoryType.WORKING,
                importanceScore = 0.20f,
                rationale = "Ephemeral daily statement discarded per memory governance rules.",
                sanitizedContent = statement
            )
        }

        // 4. Preference Memory Candidate
        if (preferenceMarkers.any { lower.contains(it) }) {
            AryaLogger.i(TAG, "Admitted high-value Preference memory: '$statement'")
            return MemoryGateDecision(
                shouldStore = true,
                type = MemoryType.PREFERENCE,
                importanceScore = 0.90f,
                rationale = "Explicit user preference detected.",
                sanitizedContent = statement
            )
        }

        // 5. People / Entity Memory Candidate
        if (peopleMarkers.any { lower.contains(it) }) {
            AryaLogger.i(TAG, "Admitted People/Entity memory: '$statement'")
            return MemoryGateDecision(
                shouldStore = true,
                type = MemoryType.PEOPLE,
                importanceScore = 0.85f,
                rationale = "Social/entity relationship data detected.",
                sanitizedContent = statement
            )
        }

        // 6. Temporal / Meeting Memory Candidate (Expires after 48 hours)
        if (temporalMeetingMarkers.any { lower.contains(it) }) {
            val twoDaysMs = 48 * 3600 * 1000L
            val expiresAt = System.currentTimeMillis() + twoDaysMs
            AryaLogger.i(TAG, "Admitted Temporary Event memory (expires in 48h): '$statement'")
            return MemoryGateDecision(
                shouldStore = true,
                type = MemoryType.EPISODIC,
                importanceScore = 0.75f,
                expirationMs = expiresAt,
                rationale = "Temporary scheduled event admitted with 48h expiration.",
                sanitizedContent = statement
            )
        }

        // Default: If statement is sufficiently informative and long (> 20 chars), store with moderate score
        val isExplicitSave = lower.startsWith("remember") || lower.startsWith("note that")
        val score = if (isExplicitSave) 0.80f else 0.40f

        return MemoryGateDecision(
            shouldStore = score >= ADMISSION_THRESHOLD,
            type = if (isExplicitSave) MemoryType.SEMANTIC else MemoryType.WORKING,
            importanceScore = score,
            rationale = if (isExplicitSave) "Explicit save command honored." else "General conversation statement below admission threshold.",
            sanitizedContent = statement
        )
    }

    companion object {
        private const val TAG = "MemoryGate"
        const val ADMISSION_THRESHOLD = 0.65f
    }
}
