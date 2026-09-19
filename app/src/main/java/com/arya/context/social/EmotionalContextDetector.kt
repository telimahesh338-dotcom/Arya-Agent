package com.arya.context.social

import com.arya.diagnostics.AryaLogger

/**
 * Engineered emotional and social context detector for ARYA.
 * Analyzes conversational cues (urgency, frustration, confusion, satisfaction, uncertainty)
 * to adjust execution speed, verbosity, confirmation thresholds, and response tone.
 *
 * NOTE: This is an engineered human-computer interaction adaptation model, not sentient emotion.
 */
class EmotionalContextDetector {

    private val frustrationMarkers = listOf(
        "stop doing", "wrong", "why did you", "annoying", "broken", "failed again",
        "useless", "idiot", "hate this", "not working", "stupid", "ugh", "wtf"
    )

    private val urgencyMarkers = listOf(
        "asap", "hurry", "quick", "fast", "urgent", "right now", "emergency", "immediately"
    )

    private val confusionMarkers = listOf(
        "what is happening", "i don't understand", "why is it", "confused", "what does this mean",
        "how do i", "lost", "where is"
    )

    private val satisfactionMarkers = listOf(
        "thank you", "thanks", "great", "perfect", "awesome", "good job", "excellent", "love it"
    )

    private val uncertaintyMarkers = listOf(
        "not sure", "maybe", "i guess", "perhaps", "should i", "is it safe", "might be", "doubt"
    )

    fun detect(input: String): EmotionalContext {
        val lower = input.lowercase().trim()
        val detected = mutableListOf<String>()

        // 1. Check Urgency
        for (kw in urgencyMarkers) {
            if (lower.contains(kw)) detected.add(kw)
        }
        if (detected.isNotEmpty() || input.contains("!")) {
            val intensity = (0.6f + (detected.size * 0.15f)).coerceAtMost(1.0f)
            AryaLogger.d(TAG, "Detected URGENCY signal (intensity=$intensity)")
            return EmotionalContext(
                dominantSignal = EmotionalSignal.URGENCY,
                intensity = intensity,
                detectedKeywords = detected
            )
        }

        // 2. Check Frustration
        for (kw in frustrationMarkers) {
            if (lower.contains(kw)) detected.add(kw)
        }
        val hasAllCaps = input.length > 5 && input.all { it.isUpperCase() || !it.isLetter() }
        if (detected.isNotEmpty() || hasAllCaps) {
            val intensity = (0.7f + (detected.size * 0.1f)).coerceAtMost(1.0f)
            AryaLogger.d(TAG, "Detected FRUSTRATION signal (intensity=$intensity)")
            return EmotionalContext(
                dominantSignal = EmotionalSignal.FRUSTRATION,
                intensity = intensity,
                detectedKeywords = detected
            )
        }

        // 3. Check Confusion
        for (kw in confusionMarkers) {
            if (lower.contains(kw)) detected.add(kw)
        }
        if (detected.isNotEmpty() || input.count { it == '?' } >= 2) {
            AryaLogger.d(TAG, "Detected CONFUSION signal")
            return EmotionalContext(
                dominantSignal = EmotionalSignal.CONFUSION,
                intensity = 0.75f,
                detectedKeywords = detected
            )
        }

        // 4. Check Satisfaction
        for (kw in satisfactionMarkers) {
            if (lower.contains(kw)) detected.add(kw)
        }
        if (detected.isNotEmpty()) {
            AryaLogger.d(TAG, "Detected SATISFACTION signal")
            return EmotionalContext(
                dominantSignal = EmotionalSignal.SATISFACTION,
                intensity = 0.80f,
                detectedKeywords = detected
            )
        }

        // 5. Check Uncertainty
        for (kw in uncertaintyMarkers) {
            if (lower.contains(kw)) detected.add(kw)
        }
        if (detected.isNotEmpty()) {
            AryaLogger.d(TAG, "Detected UNCERTAINTY signal")
            return EmotionalContext(
                dominantSignal = EmotionalSignal.UNCERTAINTY,
                intensity = 0.70f,
                detectedKeywords = detected
            )
        }

        return EmotionalContext(
            dominantSignal = EmotionalSignal.NEUTRAL,
            intensity = 0.0f
        )
    }

    /**
     * Translates detected emotional context into concrete system execution policies.
     */
    fun getAdaptivePolicy(context: EmotionalContext): AdaptiveBehaviorPolicy {
        return when (context.dominantSignal) {
            EmotionalSignal.URGENCY -> AdaptiveBehaviorPolicy(
                tone = SocialTone.URGENT_SWIFT,
                verbosityLevel = 1,
                paceDelayMs = 250L,
                requireExtraConfirmation = false,
                systemPromptDirective = "The operator is in a rush. Keep replies strictly under 15 words. Execute directly without preamble."
            )

            EmotionalSignal.FRUSTRATION -> AdaptiveBehaviorPolicy(
                tone = SocialTone.EMPATHETIC_CALM,
                verbosityLevel = 2,
                paceDelayMs = 700L,
                requireExtraConfirmation = false,
                systemPromptDirective = "The operator is frustrated. Be calm, polite, acknowledge the exact failure, and focus entirely on solution."
            )

            EmotionalSignal.CONFUSION -> AdaptiveBehaviorPolicy(
                tone = SocialTone.INSTRUCTIVE_DETAILED,
                verbosityLevel = 4,
                paceDelayMs = 800L,
                requireExtraConfirmation = true,
                systemPromptDirective = "The operator is confused. Provide structured, step-by-step clarity explaining what ARYA is doing."
            )

            EmotionalSignal.UNCERTAINTY -> AdaptiveBehaviorPolicy(
                tone = SocialTone.DIRECT_CONCISE,
                verbosityLevel = 3,
                paceDelayMs = 600L,
                requireExtraConfirmation = true,
                systemPromptDirective = "The operator is uncertain. Always ask for explicit confirmation before executing non-trivial actions."
            )

            EmotionalSignal.SATISFACTION -> AdaptiveBehaviorPolicy(
                tone = SocialTone.CHEERFUL_WARM,
                verbosityLevel = 2,
                paceDelayMs = 500L,
                requireExtraConfirmation = false,
                systemPromptDirective = "The operator is satisfied. Maintain friendly, professional demeanor."
            )

            EmotionalSignal.NEUTRAL -> AdaptiveBehaviorPolicy(
                tone = SocialTone.OBJECTIVE_PROFESSIONAL,
                verbosityLevel = 3,
                paceDelayMs = 500L,
                requireExtraConfirmation = false,
                systemPromptDirective = "Maintain clear, helpful, professional digital operator interaction."
            )
        }
    }

    companion object {
        private const val TAG = "EmotionalDetector"
    }
}
