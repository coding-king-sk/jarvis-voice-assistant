package com.rehan.jarvis.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import com.rehan.jarvis.llm.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pehle Gemini TTS try karta hai (natural awaaz).
 * Fail ho jaaye ya internet na ho to Android ka built-in TextToSpeech.
 */
class TtsManager(private val context: Context, private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false
    private var audioTrack: AudioTrack? = null

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
        val clean = sanitize(text)

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

    /** Gemini raw PCM deta hai: 24kHz, 16-bit, mono. */
    private suspend fun playPcm(pcm: ByteArray) = withContext(Dispatchers.IO) {
        try {
            stopSpeaking()
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_TTS,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(pcm.size.coerceAtMost(64 * 1024))

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
            while (offset < pcm.size) {
                val chunk = minOf(4096, pcm.size - offset)
                val written = track.write(pcm, offset, chunk)
                if (written <= 0) break
                offset += written
            }
            track.stop()
            track.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "PCM playback failed", e)
        }
    }

    private fun speakWithAndroidTts(text: String) {
        if (!androidTtsReady) {
            Log.w(TAG, "Android TTS ready nahi hai")
            return
        }
        androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
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

        /** Gemini ki available voices. */
        val VOICES = listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")
    }

    @Suppress("unused")
    private fun unusedAudioManagerRef(am: AudioManager) = am
}
