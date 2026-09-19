package com.arya.android.privileged

import com.arya.android.CapabilityManager
import com.arya.diagnostics.AryaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PrivilegedBridgeType {
    NONE,
    SHIZUKU,
    ROOT
}

/**
 * Optional Privileged Operations Bridge for ARYA.
 *
 * CRITICAL POLICY:
 * Root/Shizuku is strictly OPTIONAL. ARYA functions autonomously via standard Android Accessibility.
 * When privileged bridges are unavailable, operations gracefully degrade to standard non-root fallbacks.
 * Never attempts unauthorized privilege escalation.
 */
class PrivilegedExecutor(
    private val capabilityManager: CapabilityManager
) {

    val activeBridge: PrivilegedBridgeType
        get() {
            val caps = capabilityManager.capabilities.value
            return when {
                caps.isShizukuAvailable -> PrivilegedBridgeType.SHIZUKU
                caps.hasRootAccess -> PrivilegedBridgeType.ROOT
                else -> PrivilegedBridgeType.NONE
            }
        }

    val isPrivilegedExecutionAvailable: Boolean
        get() = activeBridge != PrivilegedBridgeType.NONE

    /**
     * Executes a fast app termination using Shizuku or Root shell if available.
     * Returns true if executed via privileged bridge, false if caller should fall back to Settings UI.
     */
    suspend fun forceStopApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        when (activeBridge) {
            PrivilegedBridgeType.SHIZUKU -> {
                AryaLogger.i(TAG, "Executing force-stop for $packageName via Shizuku shell bridge.")
                executeShizukuCommand("am force-stop $packageName")
            }
            PrivilegedBridgeType.ROOT -> {
                AryaLogger.i(TAG, "Executing force-stop for $packageName via Root shell.")
                executeRootCommand("am force-stop $packageName")
            }
            PrivilegedBridgeType.NONE -> {
                AryaLogger.d(TAG, "No privileged bridge available for force-stop. Degraded to standard fallback.")
                false
            }
        }
    }

    private fun executeRootCommand(cmd: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exit = process.waitFor()
            exit == 0
        } catch (t: Throwable) {
            AryaLogger.w(TAG, "Root command execution failed: ${t.message}")
            false
        }
    }

    private fun executeShizukuCommand(cmd: String): Boolean {
        return try {
            val shizukuClass = Class.forName("dev.rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
            val exit = process?.waitFor() ?: 1
            exit == 0
        } catch (t: Throwable) {
            AryaLogger.w(TAG, "Shizuku command execution failed: ${t.message}")
            false
        }
    }

    companion object {
        private const val TAG = "PrivilegedExecutor"
    }
}
