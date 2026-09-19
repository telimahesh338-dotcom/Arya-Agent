package com.arya.voice

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    INTERRUPTED,
    MUTED,
    ERROR
}

data class SpeechRecognitionResult(
    val transcript: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f
)
