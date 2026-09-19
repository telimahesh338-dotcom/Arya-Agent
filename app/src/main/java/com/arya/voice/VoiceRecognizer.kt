package com.arya.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.arya.diagnostics.AryaLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Native SpeechRecognizer wrapper for ARYA.
 * Supports Push-to-Talk, partial speech streaming, and wake-word extraction ("Hey Arya", "Arya").
 */
class VoiceRecognizer(private val context: Context) : RecognitionListener {

    private var recognizer: SpeechRecognizer? = null

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _speechResults = MutableSharedFlow<SpeechRecognitionResult>(extraBufferCapacity = 16)
    val speechResults: SharedFlow<SpeechRecognitionResult> = _speechResults.asSharedFlow()

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(continuous: Boolean = false) {
        if (!isAvailable) {
            AryaLogger.w(TAG, "SpeechRecognizer not available on this device.")
            _voiceState.value = VoiceState.ERROR
            return
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceRecognizer)
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            recognizer?.startListening(intent)
            _voiceState.value = VoiceState.LISTENING
            AryaLogger.d(TAG, "SpeechRecognizer started listening.")
        } catch (t: Throwable) {
            AryaLogger.e(TAG, "Failed to start SpeechRecognizer: ${t.message}", t)
            _voiceState.value = VoiceState.ERROR
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
            _voiceState.value = VoiceState.PROCESSING
        } catch (_: Throwable) {
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun cancel() {
        try {
            recognizer?.cancel()
        } catch (_: Throwable) {}
        _voiceState.value = VoiceState.IDLE
    }

    override fun onReadyForSpeech(params: Bundle?) {
        _voiceState.value = VoiceState.LISTENING
    }

    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _voiceState.value = VoiceState.PROCESSING
    }

    override fun onError(error: Int) {
        AryaLogger.w(TAG, "SpeechRecognizer error code: $error")
        _voiceState.value = VoiceState.IDLE
    }

    override fun onResults(results: Bundle?) {
        _voiceState.value = VoiceState.IDLE
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val bestText = matches?.firstOrNull() ?: return

        AryaLogger.i(TAG, "Speech recognized: '$bestText'")
        _speechResults.tryEmit(
            SpeechRecognitionResult(
                transcript = bestText,
                isFinal = true
            )
        )
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: return
        _speechResults.tryEmit(
            SpeechRecognitionResult(
                transcript = text,
                isFinal = false
            )
        )
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        cancel()
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        private const val TAG = "VoiceRecognizer"

        /**
         * Checks and extracts command from wake phrase ("Hey Arya, open Chrome" -> "open Chrome").
         */
        fun extractWakeCommand(transcript: String): String? {
            val lower = transcript.lowercase().trim()
            val wakePrefixes = listOf("hey arya", "ok arya", "arya")
            for (prefix in wakePrefixes) {
                if (lower.startsWith(prefix)) {
                    val remainder = lower.substring(prefix.length).trim().removePrefix(",").trim()
                    return if (remainder.isNotBlank()) remainder else null
                }
            }
            return null
        }
    }
}
