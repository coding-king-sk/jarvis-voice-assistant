package com.rehan.jarvis.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.rehan.jarvis.llm.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Pehle Gemini TTS try karta hai (natural awaaz).
 * Fail ho jaaye ya internet na ho to Android ka built-in TextToSpeech.
 *
 * Zaroori baat: speak() tab tak return NAHI karta jab tak bolna poora khatam na ho.
 * Isi wajah se ab awaaz beech me nahi katti.
 */
class TtsManager(private val context: Context, private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var stopped = false

    /** Chhote common jawab cache kar lete hain — latency bachti hai. */
    private val cache = mutableMapOf<String, ByteArray>()

    var voiceName: String = "Kore"
    var useGeminiTts: Boolean = true

    fun init() {
        androidTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                androidTts?.language = Locale("en", "IN")
                androidTts?.setSpeechRate(1.05f)
                androidTtsReady = true
            } else {
                Log.w(TAG, "Android TTS init failed")
            }
        }
    }

    suspend fun speak(text: String) {
        if (text.isBlank()) return
        stopped = false

        // Lamba jawab ek saath bhejne pe TTS beech me kaat deta hai,
        // isliye use chhote hisson me tod kar ek ek karke bolte hain.
        for (chunk in chunk(sanitize(text))) {
            if (stopped) return
            speakChunk(chunk)
        }
    }

    private suspend fun speakChunk(clean: String) {
        if (clean.isBlank()) return

        if (useGeminiTts && apiKey.isNotBlank()) {
            val pcm = cache[clean] ?: fetchGeminiAudio(clean)
            if (pcm != null) {
                if (clean.length <= 40) cache[clean] = pcm
                playPcm(pcm)
                return
            }
        }
        speakWithAndroidTts(clean)
    }

    /** Markdown/emoji hata do — TTS unhe padh deta hai. */
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

        for (sentence in text.split(Regex("(?<=[.!?।])\\s+"))) {
            var part = sentence.trim()
            if (part.isEmpty()) continue

            // Ek hi sentence bahut lamba ho to use bhi tod do
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

    private suspend fun fetchGeminiAudio(text: String): ByteArray? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put(
                                "prebuiltVoiceConfig",
                                JSONObject().put("voiceName", voiceName)
                            )
                        )
                    )
            )

        val request = Request.Builder()
            .url("${GeminiClient.BASE_URL}/$TTS_MODEL:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody(GeminiClient.JSON))
            .build()

        try {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Gemini TTS HTTP ${resp.code}, Android TTS pe fallback")
                    return@withContext null
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                val b64 = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optJSONObject("inlineData")
                    ?.optString("data")
                    ?: return@withContext null
                Base64.decode(b64, Base64.DEFAULT)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini TTS failed: ${e.message}")
            null
        }
    }

    /**
     * Gemini raw PCM deta hai: 24kHz, 16-bit, mono.
     *
     * Purana bug: write() ke turant baad release() call ho jaata tha, jisse
     * buffer me bacha hua audio kat jaata tha. Ab hum playback head ka intezaar
     * karte hain.
     */
    private suspend fun playPcm(pcm: ByteArray) = withContext(Dispatchers.IO) {
        try {
            releaseTrack()

            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_TTS,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(16 * 1024)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE_TTS)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            var offset = 0
            while (offset < pcm.size && !stopped) {
                val chunk = minOf(4096, pcm.size - offset)
                val written = track.write(pcm, offset, chunk)
                if (written <= 0) break
                offset += written
            }

            if (!stopped) {
                // Buffer me jo bacha hai use bajne do, tabhi release karo
                val totalFrames = pcm.size / 2
                val maxWaitMs = (totalFrames * 1000L / SAMPLE_RATE_TTS) + 800L
                val deadline = System.currentTimeMillis() + maxWaitMs
                while (!stopped &&
                    track.playbackHeadPosition < totalFrames &&
                    System.currentTimeMillis() < deadline
                ) {
                    Thread.sleep(30)
                }
            }

            releaseTrack()
        } catch (e: Exception) {
            Log.e(TAG, "PCM playback failed", e)
            releaseTrack()
        }
    }

    /** Android TTS — bolna khatam hone tak wait karta hai. */
    private suspend fun speakWithAndroidTts(text: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            val tts = androidTts
            if (!androidTtsReady || tts == null) {
                Log.w(TAG, "Android TTS ready nahi hai")
                if (cont.isActive) cont.resume(Unit)
                return@suspendCancellableCoroutine
            }

            val id = "jarvis-${System.currentTimeMillis()}"
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (cont.isActive) cont.resume(Unit)
                }

                @Deprecated("Purane Android ke liye zaroori hai")
                override fun onError(utteranceId: String?) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (cont.isActive) cont.resume(Unit)
                }
            })

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            cont.invokeOnCancellation { tts.stop() }
        }

    private fun releaseTrack() {
        try {
            audioTrack?.let {
                it.pause()
                it.flush()
                it.release()
            }
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    fun stopSpeaking() {
        stopped = true
        releaseTrack()
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
        private const val TTS_MODEL = "gemini-2.5-flash-preview-tts"
        private const val SAMPLE_RATE_TTS = 24000
        private const val MAX_CHUNK = 220

        /** Gemini ki available voices. */
        val VOICES = listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")
    }
}
