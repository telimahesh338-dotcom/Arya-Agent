package com.arya.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.arya.AryaApplication
import com.arya.agent.AgentEngine
import com.arya.ui.console.AgentConsoleScreen
import com.arya.ui.overlay.AgentFloatingOverlay
import com.arya.ui.theme.AryaTheme

class MainActivity : ComponentActivity() {

    private lateinit var agentEngine: AgentEngine
    private lateinit var floatingOverlay: AgentFloatingOverlay

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val app = AryaApplication.instance
            app.capabilityManager.setMediaProjectionReady(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = AryaApplication.instance
        agentEngine = AgentEngine(this)
        floatingOverlay = AgentFloatingOverlay(this, agentEngine.broker)

        setContent {
            AryaTheme {
                AgentConsoleScreen(
                    engine = agentEngine,
                    capabilityManager = app.capabilityManager,
                    onLaunchMediaProjection = { launchMediaProjectionRequest() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AryaApplication.instance.capabilityManager.refreshCapabilities()
    }

    override fun onStart() {
        super.onStart()
        floatingOverlay.show()
    }

    override fun onStop() {
        super.onStop()
        // Overlay stays visible across apps unless application is destroyed
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingOverlay.hide()
    }

    private fun launchMediaProjectionRequest() {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
    }
}
