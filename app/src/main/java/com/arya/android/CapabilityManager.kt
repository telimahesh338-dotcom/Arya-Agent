package com.arya.android

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Snapshot of device and system capabilities available to ARYA.
 */
data class DeviceCapabilities(
    val hasAccessibility: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasRecordAudio: Boolean = false,
    val hasPostNotifications: Boolean = false,
    val hasMediaProjectionReady: Boolean = false,
    val isShizukuAvailable: Boolean = false,
    val hasRootAccess: Boolean = false
) {
    val isReadyForAutonomousOperation: Boolean
        get() = hasAccessibility && hasOverlayPermission
}

/**
 * Manages detection and verification of system capabilities.
 * Root is strictly OPTIONAL and never mandated.
 */
class CapabilityManager(private val context: Context) {

    private val _capabilities = MutableStateFlow(DeviceCapabilities())
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

    fun refreshCapabilities() {
        val hasAccessibility = checkAccessibilityEnabled()
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val isShizuku = checkShizukuAvailable()
        val hasRoot = checkRootAvailable()

        _capabilities.value = _capabilities.value.copy(
            hasAccessibility = hasAccessibility,
            hasOverlayPermission = hasOverlay,
            hasRecordAudio = hasAudio,
            hasPostNotifications = hasNotifications,
            isShizukuAvailable = isShizuku,
            hasRootAccess = hasRoot
        )
    }

    fun setMediaProjectionReady(ready: Boolean) {
        _capabilities.value = _capabilities.value.copy(hasMediaProjectionReady = ready)
    }

    private fun checkAccessibilityEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val expectedPackage = context.packageName
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == expectedPackage }
    }

    private fun checkShizukuAvailable(): Boolean {
        return try {
            val clz = Class.forName("dev.rikka.shizuku.Shizuku")
            val ping = clz.getMethod("pingBinder")
            ping.invoke(null) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private fun checkRootAvailable(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/.su"
        )
        return paths.any { File(it).exists() }
    }
}
