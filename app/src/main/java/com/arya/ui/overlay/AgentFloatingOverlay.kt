package com.arya.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.arya.agent.AgentBroker
import com.arya.agent.AgentRunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Floating Overlay Manager displaying an always-on-top interactive widget with emergency stop.
 */
class AgentFloatingOverlay(
    private val context: Context,
    private val broker: AgentBroker
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var observerJob: Job? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (!Settings.canDrawOverlays(context) || overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xEE0A0E17.toInt())
            elevation = 16f
        }

        val statusText = TextView(context).apply {
            text = "ARYA: IDLE"
            setTextColor(0xFF00E5FF.toInt())
            textSize = 12f
            setPadding(0, 0, 16, 0)
        }

        val stopButton = Button(context).apply {
            text = "STOP"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFFF5252.toInt())
            textSize = 11f
            setOnClickListener {
                broker.stop()
            }
        }

        container.addView(statusText)
        container.addView(stopButton)

        // Drag-to-reposition logic
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - touchStartX).toInt()
                    params.y = initialY + (event.rawY - touchStartY).toInt()
                    windowManager.updateViewLayout(v, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, params)
        overlayView = container

        // Observe broker status
        observerJob = scope.launch {
            broker.status.collectLatest { status ->
                statusText.text = "ARYA: ${status.name}"
                when (status) {
                    AgentRunStatus.RUNNING -> statusText.setTextColor(0xFF00E676.toInt())
                    AgentRunStatus.STOPPED, AgentRunStatus.FAILED -> statusText.setTextColor(0xFFFF5252.toInt())
                    AgentRunStatus.AWAITING_CONFIRMATION -> statusText.setTextColor(0xFFFFD600.toInt())
                    else -> statusText.setTextColor(0xFF00E5FF.toInt())
                }
            }
        }
    }

    fun hide() {
        observerJob?.cancel()
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}
