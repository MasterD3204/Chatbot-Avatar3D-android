package com.taobao.meta.avatar.asr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Android SDK speech-to-text service for Vietnamese.
 *
 * Drop-in replacement for RecognizeService — exposes the same interface:
 *   - [onRecognizeText] callback
 *   - [startRecord] / [stopRecord]
 *   - [initRecognizer] (no-op, kept for API compatibility)
 *
 * SpeechRecognizer MUST be created and used on the Main thread.
 * All public methods are safe to call from any thread (internally
 * posts to main thread via [mainHandler]).
 */
class AndroidSttService(private val context: Context) {

    companion object {
        private const val TAG = "AndroidSttService"
        private const val LANGUAGE = "vi-VN"
        private const val SILENCE_AFTER_SPEECH_MS = 1500L
        private const val SILENCE_MAYBE_DONE_MS   = 1000L
    }

    /** Called on the calling thread when a final recognition result arrives. */
    var onRecognizeText: ((String) -> Unit)? = null

    @Volatile private var isListening = false
    @Volatile private var isDestroyed = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // Lazy-created on main thread
    private var recognizer: SpeechRecognizer? = null

    // ── Public API (thread-safe) ─────────────────────────────────────────────

    /** No-op — kept for API compatibility with old RecognizeService. */
    suspend fun initRecognizer() {
        // SpeechRecognizer is created lazily on demand in startRecord()
    }

    fun startRecord() {
        if (isListening) {
            Log.w(TAG, "startRecord() skipped — already listening")
            return
        }
        if (!hasMicPermission()) {
            Log.e(TAG, "startRecord() — RECORD_AUDIO permission not granted")
            return
        }
        mainHandler.post {
            if (isDestroyed) return@post
            ensureRecognizer()
            isListening = true
            Log.i(TAG, "🎤 startListening()")
            try {
                recognizer?.startListening(buildIntent())
            } catch (e: Exception) {
                Log.e(TAG, "startListening failed", e)
                isListening = false
            }
        }
    }

    fun stopRecord() {
        if (!isListening) return
        mainHandler.post {
            isListening = false
            Log.i(TAG, "🛑 stopListening()")
            try {
                recognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "stopListening error", e)
            }
        }
    }

    fun destroy() {
        isDestroyed = true
        isListening = false
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
            Log.i(TAG, "💀 AndroidSttService destroyed")
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun ensureRecognizer() {
        if (recognizer != null) return

        val available = SpeechRecognizer.isRecognitionAvailable(context)
        Log.i(TAG, "isRecognitionAvailable=$available, language=$LANGUAGE")

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.i(TAG, "🟢 onReadyForSpeech")
                    isListening = true
                }

                override fun onBeginningOfSpeech() {
                    Log.i(TAG, "🗣️ onBeginningOfSpeech")
                }

                override fun onEndOfSpeech() {
                    Log.i(TAG, "🔴 onEndOfSpeech")
                    isListening = false
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()

                    Log.i(TAG, "✅ onResults: \"$text\"")
                    if (!text.isNullOrBlank()) {
                        onRecognizeText?.invoke(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Partial results not forwarded upstream — final results only
                }

                override fun onError(error: Int) {
                    isListening = false
                    val name = errorName(error)
                    Log.e(TAG, "❌ onError: $name (code=$error)")

                    when (error) {
                        // Non-fatal: no speech / no match — silently ignore
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            Log.w(TAG, "⚠️ $name — ignored, ready for next utterance")
                        }
                        // Recognizer was busy — recreate on next startRecord()
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            Log.w(TAG, "Recognizer busy — will recreate on next call")
                            recognizer?.destroy()
                            recognizer = null
                        }
                        else -> {
                            Log.e(TAG, "Hard error $name — will recreate recognizer")
                            recognizer?.destroy()
                            recognizer = null
                        }
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                     RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                     SILENCE_AFTER_SPEECH_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                     SILENCE_MAYBE_DONE_MS)
        }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun errorName(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT         -> "NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NETWORK                 -> "NETWORK"
        SpeechRecognizer.ERROR_AUDIO                   -> "AUDIO"
        SpeechRecognizer.ERROR_SERVER                  -> "SERVER"
        SpeechRecognizer.ERROR_CLIENT                  -> "CLIENT"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT          -> "SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH                -> "NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY         -> "RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS"
        else -> "UNKNOWN($code)"
    }
}
