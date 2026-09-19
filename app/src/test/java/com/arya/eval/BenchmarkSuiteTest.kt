package com.arya.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkSuiteTest {

    private val suite = BenchmarkSuite()

    @Test
    fun testAll21CapabilitiesPass() {
        val summary = suite.runAllEvaluations()
        assertEquals(21, summary.totalScenarios)
        assertEquals(21, summary.passedScenarios)
        assertEquals(0, summary.failedScenarios)
        assertEquals(100.0f, summary.passRatePercentage, 0.01f)
    }

    @Test
    fun testLongHorizonStabilitySimulation() {
        // Simulates 30 consecutive state-action-reflection steps
        val suite = BenchmarkSuite()
        var healthySteps = 0

        for (step in 1..30) {
            val res = suite.runAllEvaluations()
            if (res.passedScenarios == 21) {
                healthySteps++
            }
        }

        assertEquals(30, healthySteps)
    }
}
