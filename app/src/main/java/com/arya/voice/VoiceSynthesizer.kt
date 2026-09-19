package com.arya.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.arya.diagnostics.AryaLogger
import java.util.Locale
import java.util.UUID

/**
 * Android TextToSpeech engine wrapper for ARYA.
 * Supports instant speech interruption and utterance tracking.
 */
class VoiceSynthesizer(
    context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    var isInitialized: Boolean = false
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isInitialized = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            AryaLogger.i(TAG, "TextToSpeech initialized: success=$isInitialized")
            onInitComplete?.invoke(isInitialized)
        } else {
            AryaLogger.w(TAG, "TextToSpeech initialization failed (status=$status)")
            isInitialized = false
            onInitComplete?.invoke(false)
        }
    }

    /**
     * Speaks the given text with instant queue flush (interrupts any current speech).
     */
    fun speak(text: String, onDone: (() -> Unit)? = null): Boolean {
        if (!isInitialized || tts == null) {
            AryaLogger.w(TAG, "Cannot speak: TTS not initialized.")
            return false
        }

        stop() // Immediate interruption of any ongoing speech

        val utteranceId = "arya_speech_" + UUID.randomUUID().toString().take(6)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                AryaLogger.d(TAG, "Speech started: $id")
            }

            override fun onDone(id: String?) {
                AryaLogger.d(TAG, "Speech completed: $id")
                onDone?.invoke()
            }

            override fun onError(id: String?) {
                AryaLogger.w(TAG, "Speech error on utterance: $id")
            }
        })

        val params = Bundle()
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    /**
     * Instantly halts speech synthesis (interruption capability).
     */
    fun stop() {
        if (tts?.isSpeaking == true) {
            AryaLogger.d(TAG, "Speech interrupted by operator.")
            tts?.stop()
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "VoiceSynthesizer"
    }
}
