package com.arya.diagnostics

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Production-grade structured logger for ARYA.
 * Masks secrets, passwords, API keys, credentials, and PII before recording logs.
 */
object AryaLogger {

    private const val TAG = "ARYA_CORE"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val _logFlow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 256)
    val logFlow: SharedFlow<LogEntry> = _logFlow.asSharedFlow()

    private val secretPatterns = listOf(
        // API Keys (OpenAI, Anthropic, Gemini, generic bearer tokens)
        Pattern.compile("(sk-[a-zA-Z0-9_-]{20,})"),
        Pattern.compile("(AIza[0-9A-Za-z-_]{30,45})"),
        Pattern.compile("(anthropic-[a-zA-Z0-9_-]{20,})"),
        Pattern.compile("(Bearer\\s+[a-zA-Z0-9._-]{20,})", Pattern.CASE_INSENSITIVE),
        // Passwords, PINs, tokens in key-value pairs
        Pattern.compile("(password|passwd|pin|secret|token|api_key|apikey)\\s*[:=]\\s*['\"]?([^'\"\\s]+)['\"]?", Pattern.CASE_INSENSITIVE),
        // Credit card numbers
        Pattern.compile("\\b(?:\\d{4}[ -]?){3}\\d{4}\\b")
    )

    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String
    ) {
        val formattedTimestamp: String
            get() = dateFormat.format(Date(timestamp))

        override fun toString(): String {
            return "[$formattedTimestamp] [${level.name}] [$tag] $message"
        }
    }

    enum class LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    fun d(tag: String = TAG, message: String) {
        log(LogLevel.DEBUG, tag, message)
    }

    fun i(tag: String = TAG, message: String) {
        log(LogLevel.INFO, tag, message)
    }

    fun w(tag: String = TAG, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message | Exception: ${throwable.localizedMessage}" else message
        log(LogLevel.WARN, tag, msg)
    }

    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message | Exception: ${throwable.localizedMessage}" else message
        log(LogLevel.ERROR, tag, msg)
    }

    private fun log(level: LogLevel, tag: String, rawMessage: String) {
        val sanitized = sanitize(rawMessage)
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = sanitized
        )

        when (level) {
            LogLevel.DEBUG -> Log.d(tag, sanitized)
            LogLevel.INFO -> Log.i(tag, sanitized)
            LogLevel.WARN -> Log.w(tag, sanitized)
            LogLevel.ERROR -> Log.e(tag, sanitized)
        }

        _logFlow.tryEmit(entry)
    }

    /**
     * Replaces sensitive data with [REDACTED] masks.
     */
    fun sanitize(input: String): String {
        var result = input
        for (pattern in secretPatterns) {
            val matcher = pattern.matcher(result)
            if (matcher.find()) {
                result = matcher.replaceAll("[REDACTED]")
            }
        }
        return result
    }
}
