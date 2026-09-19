package com.arya.security

import com.arya.actions.ActionType
import com.arya.agent.AgentBroker
import com.arya.agent.AgentRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskManagerTest {

    private val broker = AgentBroker()
    private val riskManager = RiskManager(broker)

    @Test
    fun testSafeActionAllowedImmediately() {
        val action = ActionType.Scroll()
        val (allowed, eval) = riskManager.checkAndIntercept(action, "Reading article")

        assertTrue(allowed)
        assertEquals(RiskLevel.LOW, eval.level)
        assertFalse(eval.requiresApproval)
    }

    @Test
    fun testSensitiveTextEntryIntercepted() {
        val action = ActionType.TypeText(text = "MyBankPassword123!")
        val (allowed, eval) = riskManager.checkAndIntercept(action, "Logging into bank")

        assertFalse("Sensitive text entry must be intercepted", allowed)
        assertEquals(RiskLevel.HIGH, eval.level)
        assertTrue(eval.requiresApproval)
        assertEquals(AgentRunStatus.AWAITING_CONFIRMATION, broker.status.value)
    }

    @Test
    fun testCriticalPaymentIntercepted() {
        val action = ActionType.Tap(label = "Confirm Payment $49.99")
        val (allowed, eval) = riskManager.checkAndIntercept(action, "Checkout purchase")

        assertFalse("Payment operations must be intercepted", allowed)
        assertTrue(eval.requiresApproval)
        assertNotNull(riskManager.pendingApproval.value)

        // Test human approval resolution
        riskManager.resolveApproval(approved = true)
        assertEquals(AgentRunStatus.RUNNING, broker.status.value)
    }

    @Test
    fun testEmergencyStop() {
        riskManager.triggerEmergencyStop("Test HUD Button")
        assertEquals(AgentRunStatus.STOPPED, broker.status.value)
    }
}
