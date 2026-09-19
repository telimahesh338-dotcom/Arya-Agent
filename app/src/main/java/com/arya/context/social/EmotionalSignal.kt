package com.arya.context.social

import kotlinx.serialization.Serializable

enum class EmotionalSignal {
    NEUTRAL,
    FRUSTRATION,
    CONFUSION,
    URGENCY,
    SATISFACTION,
    UNCERTAINTY
}

enum class SocialTone {
    DIRECT_CONCISE,
    EMPATHETIC_CALM,
    INSTRUCTIVE_DETAILED,
    URGENT_SWIFT,
    CHEERFUL_WARM,
    OBJECTIVE_PROFESSIONAL
}

@Serializable
data class EmotionalContext(
    val dominantSignal: EmotionalSignal,
    val intensity: Float, // 0.0 to 1.0
    val confidence: Float = 0.85f,
    val detectedKeywords: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AdaptiveBehaviorPolicy(
    val tone: SocialTone,
    val verbosityLevel: Int, // 1 (ultra-concise) to 5 (detailed tutorial)
    val paceDelayMs: Long,   // Delay between action steps
    val requireExtraConfirmation: Boolean,
    val systemPromptDirective: String
)
