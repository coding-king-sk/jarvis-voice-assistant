package com.rehan.jarvis.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Android ka built-in SpeechRecognizer.
 * Hinglish ke liye locale "en-IN" sabse accha kaam karta hai —
 * ye Hindi shabdon ko roman letters me theek likh deta hai.
 *
 * IMPORTANT: saare methods main thread se call karo.
 */
class SpeechRecognizerManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onReady: (() -> Unit)? = null
    var onEndOfSpeech: (() -> Unit)? = null

    /** Awaaz kitni tez hai — orb ka waveform isse chalta hai. */
    var onRmsChanged: ((Float) -> Unit)? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (isListening) return
        if (!isAvailable()) {
            onError?.invoke("Is phone pe speech recognition available nahi hai.")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
        }

        isListening = true
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        isListening = false
        recognizer?.stopListening()
    }

    fun destroy() {
        isListening = false
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { onReady?.invoke() }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { onRmsChanged?.invoke(rmsdB) }
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            onEndOfSpeech?.invoke()
        }

        override fun onError(error: Int) {
            isListening = false
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kuch sunai nahi diya."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network problem hai."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission nahi mili."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy hai, ek second ruko."
                else -> "Sunne me dikkat aayi."
            }
            Log.w(TAG, "STT error $error -> $msg")
            onError?.invoke(msg)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isBlank()) onError?.invoke("Kuch sunai nahi diya.")
            else onFinalResult?.invoke(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { onPartialResult?.invoke(it) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object { private const val TAG = "SttManager" }
}
