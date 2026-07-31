package com.rehan.jarvis.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale

/**
 * Awaaz — poori tarah phone ke andar se.
 *
 * Pehle cloud TTS use hota tha: har jawab pe 2-3 second audio download hota tha.
 * Ab phone ka apna TextToSpeech bolta hai — bilkul turant, bina internet, bina paisa.
 *
 * Zaroori baat: speak() tab tak return NAHI karta jab tak bolna khatam na ho.
 * Isi wajah se awaaz beech me nahi katti.
 */
class TtsManager(private val context: Context) {

    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false

    @Volatile
    private var stopped = false

    /** Purane code se compatibility ke liye — ab sirf phone ki awaaz chalti hai. */
    var voiceName: String = "default"
    var useGeminiTts: Boolean = false

    /** Dost jaisi speed — thoda tez, robot jaisa slow nahi. */
    var speechRate: Float = 1.1f
        set(value) {
            field = value.coerceIn(0.6f, 1.8f)
            androidTts?.setSpeechRate(field)
        }

    fun init() {
        androidTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val tts = androidTts ?: return@TextToSpeech
                // Indian English — Hinglish isme sabse natural lagti hai
                val indian = Locale("en", "IN")
                tts.language = if (tts.isLanguageAvailable(indian) >= TextToSpeech.LANG_AVAILABLE) {
                    indian
                } else {
                    Locale.US
                }
                tts.setSpeechRate(speechRate)
                tts.setPitch(1.02f)
                androidTtsReady = true
            } else {
                Log.w(TAG, "TTS init failed")
            }
        }
    }

    suspend fun speak(text: String) {
        if (text.isBlank()) return
        stopped = false

        for (chunk in chunk(sanitize(text))) {
            if (stopped) return
            speakChunk(chunk)
        }
    }

    private suspend fun speakChunk(clean: String) {
        if (clean.isBlank()) return
        speakWithAndroidTts(clean)
    }

    /** Markdown aur emoji hata do — warna TTS unhe padh deta hai. */
    private fun sanitize(text: String): String = text
        .replace(Regex("[*_`#>\\[\\]()]"), " ")
        .replace(Regex("[\\p{So}\\p{Cn}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Sentence ke hisaab se todo, taaki beech-waak me na kate. */
    private fun chunk(text: String, max: Int = MAX_CHUNK): List<String> {
        if (text.length <= max) return listOf(text)

        val out = mutableListOf<String>()
        val builder = StringBuilder()

        for (sentence in text.split(Regex("(?<=[.!?])\\s+"))) {
            var part = sentence.trim()
            if (part.isEmpty()) continue

            while (part.length > max) {
                val cut = part.lastIndexOf(' ', max).takeIf { it > 40 } ?: max
                out.add(part.substring(0, cut).trim())
                part = part.substring(cut).trim()
            }

            if (builder.length + part.length + 1 > max) {
                if (builder.isNotEmpty()) out.add(builder.toString().trim())
                builder.setLength(0)
            }
            builder.append(part).append(' ')
        }

        if (builder.isNotBlank()) out.add(builder.toString().trim())
        return out.filter { it.isNotBlank() }
    }

    /** Bolna khatam hone tak wait karta hai. */
    private suspend fun speakWithAndroidTts(text: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            val tts = androidTts
            if (!androidTtsReady || tts == null) {
                Log.w(TAG, "TTS ready nahi hai")
                if (cont.isActive) cont.resume(Unit) { }
                return@suspendCancellableCoroutine
            }

            val id = "jarvis-${System.currentTimeMillis()}"
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (cont.isActive) cont.resume(Unit) { }
                }

                @Deprecated("Purane Android ke liye zaroori hai")
                override fun onError(utteranceId: String?) {
                    if (cont.isActive) cont.resume(Unit) { }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (cont.isActive) cont.resume(Unit) { }
                }
            })

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            cont.invokeOnCancellation { tts.stop() }
        }

    fun stopSpeaking() {
        stopped = true
        androidTts?.stop()
    }

    fun release() {
        stopSpeaking()
        androidTts?.shutdown()
        androidTts = null
        androidTtsReady = false
    }

    companion object {
        private const val TAG = "TtsManager"
        private const val MAX_CHUNK = 220

        /** Purane code se compatibility ke liye. */
        val VOICES = listOf("default")
    }
}
