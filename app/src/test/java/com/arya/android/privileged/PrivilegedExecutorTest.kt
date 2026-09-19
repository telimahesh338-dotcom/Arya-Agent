package com.arya.android.privileged

import com.arya.android.DeviceCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedExecutorTest {

    @Test
    fun testDeviceCapabilitiesAutonomyReadiness() {
        val fullCaps = DeviceCapabilities(
            hasAccessibility = true,
            hasOverlayPermission = true,
            hasRecordAudio = true
        )
        assertTrue(fullCaps.isReadyForAutonomousOperation)

        val partialCaps = DeviceCapabilities(
            hasAccessibility = true,
            hasOverlayPermission = false
        )
        assertFalse(partialCaps.isReadyForAutonomousOperation)
    }

    @Test
    fun testNonRootDegradationPolicy() {
        val nonRootCaps = DeviceCapabilities(
            hasAccessibility = true,
            hasOverlayPermission = true,
            isShizukuAvailable = false,
            hasRootAccess = false
        )

        assertFalse("Root must never be mandatory", nonRootCaps.hasRootAccess)
        assertTrue("Agent functions with Accessibility alone", nonRootCaps.isReadyForAutonomousOperation)
    }
}
