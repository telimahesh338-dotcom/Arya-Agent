package com.arya

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.arya.android.CapabilityManager
import com.arya.security.KeyVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Main Application class for ARYA Autonomous Agent.
 * Initializes hardware-backed security, notification channels, capability probes,
 * and application-wide dependency containers.
 */
class AryaApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var capabilityManager: CapabilityManager
        private set

    lateinit var keyVault: KeyVault
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()
        initializeSecurity()
        initializeCapabilities()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_EXECUTION_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun initializeSecurity() {
        keyVault = KeyVault(this)
    }

    private fun initializeCapabilities() {
        capabilityManager = CapabilityManager(this)
        capabilityManager.refreshCapabilities()
    }

    companion object {
        const val CHANNEL_EXECUTION_ID = "arya_execution_channel"
        lateinit var instance: AryaApplication
            private set
    }
}
