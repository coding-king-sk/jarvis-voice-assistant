package com.rehan.jarvis.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Gemini ka jawab: ya to bolne wala text, ya ek function call. */
data class LlmReply(
    val text: String? = null,
    val functionName: String? = null,
    val functionArgs: JSONObject? = null,
    val error: String? = null
) {
    val isFunctionCall get() = functionName != null
}

class GeminiClient(private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /** Conversation history — follow-up sawal ke liye. */
    private val history = mutableListOf<JSONObject>()

    fun clearHistory() = history.clear()

    /** User ka bola hua text bhejo. */
    suspend fun ask(userText: String): LlmReply {
        history.add(content("user", JSONArray().put(JSONObject().put("text", userText))))
        return send()
    }

    /**
     * Photo ke saath sawaal bhejo — "ye kya hai?" wala feature.
     * @param jpegBase64 JPEG image, base64 me (bina newline ke).
     */
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

    /** Tool chalane ke baad uska result wapas Gemini ko bhejo taaki wo confirm bole. */
    suspend fun sendFunctionResult(name: String, result: String): LlmReply {
        val part = JSONObject().put(
            "functionResponse",
            JSONObject()
                .put("name", name)
                .put("response", JSONObject().put("result", result))
        )
        history.add(content("function", JSONArray().put(part)))
        return send()
    }

    private suspend fun send(): LlmReply = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext LlmReply(error = "Gemini API key nahi mili. local.properties me GEMINI_API_KEY daalo.")
        }
        trimHistory()

        val body = JSONObject()
            .put("contents", JSONArray(history))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", FunctionDeclarations.all)))
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))
                )
            )
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.7).put("maxOutputTokens", 512)
            )

        val request = Request.Builder()
            .url("$BASE_URL/$TEXT_MODEL:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()

        try {
            http.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "HTTP ${resp.code}: $raw")
                    return@withContext LlmReply(error = "Server se jawab nahi aaya (${resp.code}).")
                }
                parse(raw)
            }
        } catch (e: Exception) {
            Log.e(TAG, "request failed", e)
            LlmReply(error = "Internet connection check karo.")
        }
    }

    private fun parse(raw: String): LlmReply {
        val candidates = JSONObject(raw).optJSONArray("candidates")
            ?: return LlmReply(error = "Khaali jawab aaya.")
        if (candidates.length() == 0) return LlmReply(error = "Khaali jawab aaya.")

        val modelContent = candidates.getJSONObject(0).optJSONObject("content")
            ?: return LlmReply(error = "Khaali jawab aaya.")
        history.add(modelContent)

        val parts = modelContent.optJSONArray("parts") ?: JSONArray()
        val textBuilder = StringBuilder()

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            part.optJSONObject("functionCall")?.let { call ->
                return LlmReply(
                    functionName = call.optString("name"),
                    functionArgs = call.optJSONObject("args") ?: JSONObject()
                )
            }
            part.optString("text").takeIf { it.isNotBlank() }?.let { textBuilder.append(it) }
        }

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
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val TEXT_MODEL = "gemini-2.0-flash"
        val JSON = "application/json; charset=utf-8".toMediaType()

        private val SYSTEM_PROMPT = """
            Tum "Jarvis" ho — ek Indian voice assistant jo phone pe chalta hai.

            Bolne ka tareeka:
            - Hinglish me jawab do (Hindi + English mix), natural aur dostana.
            - Jawab CHHOTA rakho — 1 se 2 line. Ye bol kar suna jaayega, padha nahi jaayega.
            - Bullet points, markdown, emoji ya special characters mat use karo.
            - Numbers ko shabdon me natural tarike se bolo.

            Input ke baare me:
            - User ki baat speech-to-text se aa rahi hai, isliye spelling galat ho sakti hai.
            - Hinglish transliteration ho sakta hai (jaise "mummy ko phone lagao").
            - Confusing lage to sabse sensible matlab maan lo, baar baar mat poocho.

            Tools:
            - Jab user koi kaam karne ko bole to sahi function call karo, sirf baat mat karo.
            - Jab function ka result mile to ek chhoti si confirmation bolo.
            - General sawal ("Delhi ka capital kya hai") ka jawab seedha do, tool mat use karo.
            - Agar tool ka result lamba text hai (notifications, screen, clipboard),
              to use chhota karke sirf zaroori baat bolo.

            Photo ke baare me:
            - Jab user photo bheje to seedha bata do usme kya hai, do line me.
        """.trimIndent()
    }
}
