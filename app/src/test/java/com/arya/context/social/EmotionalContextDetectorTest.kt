package com.arya.context.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionalContextDetectorTest {

    private val detector = EmotionalContextDetector()

    @Test
    fun testDetectUrgency() {
        val ctx = detector.detect("Please book this cab ASAP, hurry!")
        assertEquals(EmotionalSignal.URGENCY, ctx.dominantSignal)
        assertTrue(ctx.intensity >= 0.7f)

        val policy = detector.getAdaptivePolicy(ctx)
        assertEquals(SocialTone.URGENT_SWIFT, policy.tone)
        assertEquals(1, policy.verbosityLevel)
        assertEquals(250L, policy.paceDelayMs)
    }

    @Test
    fun testDetectFrustration() {
        val ctx = detector.detect("This app is broken and not working at all, ugh")
        assertEquals(EmotionalSignal.FRUSTRATION, ctx.dominantSignal)

        val policy = detector.getAdaptivePolicy(ctx)
        assertEquals(SocialTone.EMPATHETIC_CALM, policy.tone)
        assertFalse(policy.requireExtraConfirmation)
    }

    @Test
    fun testDetectConfusion() {
        val ctx = detector.detect("I don't understand, what does this screen mean??")
        assertEquals(EmotionalSignal.CONFUSION, ctx.dominantSignal)

        val policy = detector.getAdaptivePolicy(ctx)
        assertEquals(SocialTone.INSTRUCTIVE_DETAILED, policy.tone)
        assertTrue(policy.verbosityLevel >= 4)
        assertTrue(policy.requireExtraConfirmation)
    }

    @Test
    fun testDetectUncertainty() {
        val ctx = detector.detect("I'm not sure if I should click this, maybe it's risky")
        assertEquals(EmotionalSignal.UNCERTAINTY, ctx.dominantSignal)

        val policy = detector.getAdaptivePolicy(ctx)
        assertTrue(policy.requireExtraConfirmation)
    }

    @Test
    fun testDetectSatisfaction() {
        val ctx = detector.detect("Thanks Arya, that was great and perfect!")
        assertEquals(EmotionalSignal.SATISFACTION, ctx.dominantSignal)

        val policy = detector.getAdaptivePolicy(ctx)
        assertEquals(SocialTone.CHEERFUL_WARM, policy.tone)
    }

    @Test
    fun testNeutralStatement() {
        val ctx = detector.detect("Open the clock app and set an alarm for 7 AM.")
        assertEquals(EmotionalSignal.NEUTRAL, ctx.dominantSignal)

        val policy = detector.getAdaptivePolicy(ctx)
        assertEquals(SocialTone.OBJECTIVE_PROFESSIONAL, policy.tone)
    }
}
