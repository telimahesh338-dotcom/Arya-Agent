package com.arya.voice

import android.content.Context
import com.arya.agent.AgentEngine
import com.arya.agent.AgentRunStatus
import com.arya.diagnostics.AryaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Unified Voice Pipeline for ARYA.
 *
 * CRITICAL ARCHITECTURE REQUIREMENT:
 * Voice feeds directly into the unified AgentEngine. Never duplicates task intelligence.
 * Handles push-to-talk, speech synthesis, voice interruption, and verbal task telemetry.
 */
class VoicePipeline(
    private val context: Context,
    private val agentEngine: AgentEngine,
    val recognizer: VoiceRecognizer = VoiceRecognizer(context),
    val synthesizer: VoiceSynthesizer = VoiceSynthesizer(context)
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var listenerJob: Job? = null
    private var statusMonitorJob: Job? = null

    fun initialize() {
        startTranscriptListener()
        startAgentStatusSpokenTelemetry()
        AryaLogger.i(TAG, "VoicePipeline initialized and linked to AgentEngine.")
    }

    private fun startTranscriptListener() {
        listenerJob?.cancel()
        listenerJob = scope.launch {
            recognizer.speechResults.collectLatest { result ->
                if (result.isFinal && result.transcript.isNotBlank()) {
                    // Interrupt current speech synthesis immediately
                    synthesizer.stop()

                    // Check for wake phrase or direct command
                    val command = VoiceRecognizer.extractWakeCommand(result.transcript) ?: result.transcript
                    AryaLogger.i(TAG, "Voice command received: '$command'. Forwarding directly to AgentEngine.")

                    synthesizer.speak("Understood.") {
                        // Dispatch to central AgentEngine
                        agentEngine.startTask(command)
                    }
                }
            }
        }
    }

    private fun startAgentStatusSpokenTelemetry() {
        statusMonitorJob?.cancel()
        statusMonitorJob = scope.launch {
            agentEngine.broker.status.collectLatest { status ->
                when (status) {
                    AgentRunStatus.COMPLETED -> synthesizer.speak("Task completed successfully.")
                    AgentRunStatus.FAILED -> synthesizer.speak("Task failed to complete.")
                    AgentRunStatus.STOPPED -> synthesizer.speak("Task stopped by operator.")
                    AgentRunStatus.AWAITING_CONFIRMATION -> synthesizer.speak("Approval required. Please check your screen.")
                    else -> {}
                }
            }
        }
    }

    fun startPushToTalk() {
        synthesizer.stop() // Immediate interruption
        recognizer.startListening()
    }

    fun stopPushToTalk() {
        recognizer.stopListening()
    }

    fun interruptAndCancel() {
        synthesizer.stop()
        recognizer.cancel()
        agentEngine.stop()
    }

    fun destroy() {
        listenerJob?.cancel()
        statusMonitorJob?.cancel()
        recognizer.destroy()
        synthesizer.shutdown()
    }

    companion object {
        private const val TAG = "VoicePipeline"
    }
}
