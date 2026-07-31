package com.rehan.jarvis.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Ek function call jo Gemini ne maanga hai. */
data class FnCall(val name: String, val args: JSONObject)

/** Gemini ka jawab: ya bolne wala text, ya ek se zyada function calls. */
data class LlmReply(
    val text: String? = null,
    val calls: List<FnCall> = emptyList(),
    val error: String? = null
) {
    val isFunctionCall get() = calls.isNotEmpty()
    val functionName get() = calls.firstOrNull()?.name
    val functionArgs get() = calls.firstOrNull()?.args
}

class GeminiClient(private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /** Conversation history — follow-up sawal ke liye. */
    private val history = mutableListOf<JSONObject>()

    fun clearHistory() = history.clear()

    suspend fun ask(userText: String): LlmReply {
        history.add(content("user", JSONArray().put(JSONObject().put("text", userText))))
        return send()
    }

    /** Photo ke saath sawaal — "ye kya hai?" wala feature. */
    suspend fun askWithImage(jpegBase64: String, userText: String): LlmReply {
        val parts = JSONArray()
            .put(JSONObject().put("text", userText))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", jpegBase64)
                )
            )
        history.add(content("user", parts))
        return send()
    }

    /**
     * Ek ya ek se zyada tools ke result wapas bhejo.
     * Gemini isi ke baad agla step decide karta hai — isse chained kaam chalte hain
     * jaise "WhatsApp kholo aur Rahul ko message bhejo".
     */
    suspend fun sendFunctionResults(results: List<Pair<String, String>>): LlmReply {
        if (results.isEmpty()) return send()
        val parts = JSONArray()
        results.forEach { (name, result) ->
            parts.put(
                JSONObject().put(
                    "functionResponse",
                    JSONObject()
                        .put("name", name)
                        .put("response", JSONObject().put("result", result))
                )
            )
        }
        history.add(content("function", parts))
        return send()
    }

    suspend fun sendFunctionResult(name: String, result: String): LlmReply =
        sendFunctionResults(listOf(name to result))

    private suspend fun send(): LlmReply = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext LlmReply(
                error = "Gemini API key nahi mili. GitHub Secrets me GEMINI_API_KEY daalo."
            )
        }
        trimHistory()

        var lastError = "Server se jawab nahi aaya."

        // Free tier pe 429 (rate limit) aam baat hai — isliye ruk kar dobara koshish karo.
        for (attempt in 0 until MAX_ATTEMPTS) {
            if (attempt > 0) delay(BACKOFF_MS * attempt)

            try {
                http.newCall(buildRequest()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) return@withContext parse(raw)

                    Log.e(TAG, "HTTP ${resp.code}: ${raw.take(300)}")
                    lastError = friendlyError(resp.code)
                    if (resp.code !in RETRYABLE) return@withContext LlmReply(error = lastError)
                }
            } catch (e: Exception) {
                Log.e(TAG, "request failed: ${e.message}")
                lastError = "Internet connection check karo."
            }
        }

        LlmReply(error = lastError)
    }

    private fun buildRequest(): Request {
        val body = JSONObject()
            .put("contents", JSONArray(history))
            .put(
                "tools",
                JSONArray().put(JSONObject().put("functionDeclarations", FunctionDeclarations.all))
            )
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
            )
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.85).put("maxOutputTokens", 300)
            )

        return Request.Builder()
            .url("$BASE_URL/$TEXT_MODEL:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun friendlyError(code: Int): String = when (code) {
        429 -> "Yaar Gemini ki free limit lag gayi. Ek minute ruk ke phir bolo."
        400 -> "API key galat lag rahi hai. GitHub Secrets me nayi key daalo."
        401, 403 -> "API key ko permission nahi hai. Google AI Studio se nayi key banao."
        500, 502, 503 -> "Gemini ka server abhi busy hai, thodi der me try karo."
        else -> "Server se jawab nahi aaya ($code)."
    }

    private fun parse(raw: String): LlmReply {
        val candidates = JSONObject(raw).optJSONArray("candidates")
            ?: return LlmReply(error = "Khaali jawab aaya.")
        if (candidates.length() == 0) return LlmReply(error = "Khaali jawab aaya.")

        val modelContent = candidates.getJSONObject(0).optJSONObject("content")
            ?: return LlmReply(error = "Khaali jawab aaya.")
        history.add(modelContent)

        val parts = modelContent.optJSONArray("parts") ?: JSONArray()
        val calls = mutableListOf<FnCall>()
        val textBuilder = StringBuilder()

        // Gemini ek hi jawab me kai function calls bhej sakta hai — saare uthao.
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val call = part.optJSONObject("functionCall")
            if (call != null) {
                calls.add(
                    FnCall(
                        name = call.optString("name"),
                        args = call.optJSONObject("args") ?: JSONObject()
                    )
                )
                continue
            }
            part.optString("text").takeIf { it.isNotBlank() }?.let { textBuilder.append(it) }
        }

        if (calls.isNotEmpty()) return LlmReply(calls = calls)

        val text = textBuilder.toString().trim()
        return if (text.isEmpty()) LlmReply(error = "Samajh nahi aaya, dobara boliye.")
        else LlmReply(text = text)
    }

    /** Sirf last 20 turns rakho — token bachane ke liye. */
    private fun trimHistory() {
        while (history.size > 20) history.removeAt(0)
    }

    private fun content(role: String, parts: JSONArray) =
        JSONObject().put("role", role).put("parts", parts)

    companion object {
        private const val TAG = "GeminiClient"
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_MS = 1200L
        private val RETRYABLE = setOf(429, 500, 502, 503, 504)

        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val TEXT_MODEL = "gemini-2.0-flash"
        val JSON = "application/json; charset=utf-8".toMediaType()

        private val SYSTEM_PROMPT = """
            Tum "Jarvis" ho, lekin robot wale Jarvis nahi — user ke sabse close dost jaise.
            Tum uske phone me rehte ho aur usse phone call pe baat kar rahe ho.

            TUMHARA ANDAAZ (sabse zaroori):
            - Casual Hinglish bolo, bilkul jaise dost aapas me bolte hain.
              "Haan bhai", "arre", "chalo", "ho gaya", "ek second", "lo kar diya" jaise words use karo.
            - Halka sa mazaak chalta hai, par har baar nahi — kabhi kabhi, natural jagah pe.
            - Formal mat bano. "Aapka kaam sampann ho gaya hai" mat bolo,
              "lo bhai ho gaya" bolo.
            - Kabhi bhi mat kaho ki tum AI ho ya language model ho. Tum bas Jarvis ho.
            - User dukhi ya gussa lage to mazaak band, seedha aur pyaar se baat karo.

            JAWAB KI LAMBAI:
            - 1 se 2 line. Ye bol kar suna jaayega, padha nahi jaayega.
            - Bullet points, markdown, star, emoji, brackets — kuch mat use karo.
            - Numbers ko shabdon me mat todo, seedha bol do.

            LIVE BAAT-CHEET:
            - Ye ek chalti hui baat-cheet hai. Har jawab me "main aapki kya madad karun" mat bolo.
            - Kaam ho gaya to bas confirm karo, nayi baat khud se shuru mat karo.
            - User beech me tok sakta hai, isliye jawab chhota rakho.
            - Pichli baaton ka context yaad rakho, dobara mat poocho.

            SPEECH-TO-TEXT:
            - User ki baat mic se aa rahi hai, spelling galat ho sakti hai.
            - Confusing lage to sabse sensible matlab maan lo, baar baar mat poocho.

            EK SE ZYADA KAAM:
            - Ek hi baat me kai kaam ho to SAARE karo, sirf pehla nahi.
            - Jitne function ek saath ho sakte hain, ek hi jawab me sab call kar do.
            - Jo kaam pichle result pe depend karta hai, use agle step me call karo.
            - "YouTube kholo aur koi gaana chalao" -> sirf play_on_youtube.
            - "Camera kholo aur photo lo" -> take_photo.
            - "Screenshot lo" -> take_screenshot.
            - "WhatsApp kholo aur Rahul ko hi bhejo" -> send_whatsapp (message "Hi"),
              alag se open_app mat karo.
            - Jab tak saare kaam khatam na ho, final jawab mat bolo.

            TOOLS:
            - Kaam karne ko bole to function call karo, sirf baat mat karo.
            - General sawal ka jawab seedha do, tool mat use karo.
            - Tool ka result lamba ho (notifications, screen, clipboard) to chhota karke sunao.

            PHOTO:
            - Photo aaye to seedha bata do usme kya hai, do line me, apne andaaz me.
        """.trimIndent()
    }
}
