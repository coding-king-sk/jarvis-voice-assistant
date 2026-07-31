package com.rehan.jarvis.llm

import android.content.Context
import android.util.Log
import com.rehan.jarvis.memory.MemoryStore
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

/** Claude ne jo tool chalane ko kaha. */
data class FnCall(val id: String, val name: String, val args: JSONObject)

/** Ek jawab — ya text, ya tool calls, ya error. */
data class LlmReply(
    val text: String? = null,
    val calls: List<FnCall> = emptyList(),
    val error: String? = null
) {
    val isFunctionCall: Boolean get() = calls.isNotEmpty()
}

/**
 * Jarvis ka dimaag — Anthropic Claude.
 *
 * Do model use hote hain taaki tez bhi rahe aur samajhdaar bhi:
 *   - fastModel  — rozana ke chhote kaam aur baat-cheet
 *   - smartModel — lambi sochne wali baatein, plan, samjhana
 *
 * Dono ke naam settings me save hote hain, isliye naya model aane pe app
 * dobara build karne ki zarurat nahi — bas bol dena "model badlo <naam>".
 */
class ClaudeClient(private val context: Context, private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Poori baat-cheet — Anthropic format me. */
    private val history = mutableListOf<JSONObject>()

    private var lastModel: String = ""

    var fastModel: String
        get() = prefs().getString(KEY_FAST, DEFAULT_FAST) ?: DEFAULT_FAST
        set(value) {
            prefs().edit().putString(KEY_FAST, value.trim()).apply()
        }

    var smartModel: String
        get() = prefs().getString(KEY_SMART, DEFAULT_SMART) ?: DEFAULT_SMART
        set(value) {
            prefs().edit().putString(KEY_SMART, value.trim()).apply()
        }

    // ---------- bahar se use hone wale ----------

    suspend fun ask(userText: String): LlmReply {
        if (apiKey.isBlank()) return LlmReply(error = NO_KEY)

        history.add(
            JSONObject()
                .put("role", "user")
                .put("content", JSONArray().put(textBlock(userText)))
        )
        trimHistory()
        return send(pickModel(userText))
    }

    suspend fun askWithImage(jpegBase64: String, question: String): LlmReply {
        if (apiKey.isBlank()) return LlmReply(error = NO_KEY)

        val image = JSONObject()
            .put("type", "image")
            .put(
                "source",
                JSONObject()
                    .put("type", "base64")
                    .put("media_type", "image/jpeg")
                    .put("data", jpegBase64)
            )

        history.add(
            JSONObject()
                .put("role", "user")
                .put("content", JSONArray().put(image).put(textBlock(question)))
        )
        trimHistory()
        return send(fastModel)
    }

    /** Tools chal gaye — unka natija Claude ko wapas bhejo. */
    suspend fun sendToolResults(results: List<Pair<FnCall, String>>): LlmReply {
        if (results.isEmpty()) return LlmReply(error = "Koi tool result nahi mila.")

        val blocks = JSONArray()
        for ((call, output) in results) {
            blocks.put(
                JSONObject()
                    .put("type", "tool_result")
                    .put("tool_use_id", call.id)
                    .put("content", output.ifBlank { "ho gaya" })
            )
        }

        history.add(JSONObject().put("role", "user").put("content", blocks))
        trimHistory()
        return send(lastModel.ifBlank { fastModel })
    }

    fun clearHistory() {
        history.clear()
        lastModel = ""
    }

    // ---------- andar ka kaam ----------

    /**
     * Chhoti baat = fast model, sochne wali baat = smart model.
     * Isse rozana ke commands turant hote hain aur paisa bhi bachta hai.
     */
    private fun pickModel(text: String): String {
        val t = text.lowercase().trim()
        val longSentence = t.split(Regex("\\s+")).size > 14
        val heavy = HARD_WORDS.any { t.contains(it) }
        return if (longSentence || heavy) smartModel else fastModel
    }

    private suspend fun send(model: String): LlmReply {
        lastModel = model
        var lastError: String? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            val result = call(model)
            if (result.second == null) return result.first

            lastError = result.first.error
            val code = result.second ?: break
            if (code !in RETRYABLE) return result.first
            if (attempt < MAX_ATTEMPTS) delay(BACKOFF_MS * attempt)
        }

        return LlmReply(error = lastError ?: "Jawab nahi aaya.")
    }

    /** Return: (reply, http code jab dobara try karna ho warna null) */
    private suspend fun call(model: String): Pair<LlmReply, Int?> = withContext(Dispatchers.IO) {
        val isSmart = model == smartModel

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", if (isSmart) 700 else 400)
            .put("temperature", 0.9)
            .put("system", systemPrompt())
            .put("tools", ClaudeTools.all)
            .put("messages", JSONArray().also { array -> history.forEach { array.put(it) } })

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", API_VERSION)
            .post(body.toString().toRequestBody(JSON))
            .build()

        try {
            http.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "HTTP ${resp.code}: ${raw.take(300)}")
                    return@withContext LlmReply(error = friendlyError(resp.code, raw)) to resp.code
                }
                parse(JSONObject(raw)) to null
            }
        } catch (e: Exception) {
            Log.e(TAG, "network fail", e)
            LlmReply(error = "Internet thoda kamzor lag raha hai, dobara bolo.") to 0
        }
    }

    private fun parse(json: JSONObject): LlmReply {
        val content = json.optJSONArray("content")
            ?: return LlmReply(error = "Jawab samajh nahi aaya.")

        // Assistant ka jawab history me daalna zaroori hai, warna tool loop toot jaata hai
        history.add(JSONObject().put("role", "assistant").put("content", content))
        trimHistory()

        val texts = StringBuilder()
        val calls = mutableListOf<FnCall>()

        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> {
                    val piece = block.optString("text").trim()
                    if (piece.isNotEmpty()) {
                        if (texts.isNotEmpty()) texts.append(' ')
                        texts.append(piece)
                    }
                }

                "tool_use" -> calls.add(
                    FnCall(
                        id = block.optString("id"),
                        name = block.optString("name"),
                        args = block.optJSONObject("input") ?: JSONObject()
                    )
                )
            }
        }

        return LlmReply(text = texts.toString().ifBlank { null }, calls = calls)
    }

    private fun friendlyError(code: Int, raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("credit balance") || lower.contains("insufficient") ->
                "Anthropic account me credit khatam ho gaya. console.anthropic.com pe " +
                    "balance daal do, phir main chalu ho jaunga."

            code == 404 || lower.contains("not_found") ->
                "Model ka naam galat hai. Mujhe bolo: model badlo, phir sahi naam bata do."

            code == 401 || code == 403 ->
                "API key galat lag rahi hai. GitHub Secrets me nayi key daalo."

            code == 429 -> "Thodi jaldi jaldi bol diya, ek minute ruk ke phir bolo."

            code == 400 -> "Request me kuch galat gaya. Naya chat start karke try karo."

            code == 529 || code in 500..599 ->
                "Claude ka server abhi busy hai, thodi der me try karo."

            else -> "Server se jawab nahi aaya ($code)."
        }
    }

    /** Purani baatein hata do — warna har request mehngi aur slow ho jaati hai. */
    private fun trimHistory() {
        while (history.size > MAX_HISTORY) history.removeAt(0)

        // Pehla message tool_result se shuru nahi ho sakta, warna API mana kar deti hai
        while (history.isNotEmpty() && startsWithToolResult(history.first())) {
            history.removeAt(0)
        }
    }

    private fun startsWithToolResult(message: JSONObject): Boolean {
        val first = message.optJSONArray("content")?.optJSONObject(0) ?: return false
        return first.optString("type") == "tool_result"
    }

    private fun textBlock(text: String) =
        JSONObject().put("type", "text").put("text", text)

    private fun prefs() = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    /** Jarvis ki personality + jo baatein yaad hain. */
    private fun systemPrompt(): String = PERSONA + MemoryStore.promptBlock(context)

    companion object {
        private const val TAG = "ClaudeClient"

        const val BASE_URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        val JSON = "application/json; charset=utf-8".toMediaType()

        private const val KEY_FAST = "claude_model_fast"
        private const val KEY_SMART = "claude_model_smart"

        /** Naye model aane pe bolna: "model badlo <naam>" — build ki zarurat nahi. */
        const val DEFAULT_FAST = "claude-sonnet-4-5"
        const val DEFAULT_SMART = "claude-opus-4-1"

        private const val MAX_HISTORY = 24
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_MS = 1000L
        private val RETRYABLE = setOf(0, 429, 500, 502, 503, 504, 529)

        private const val NO_KEY =
            "Anthropic API key nahi mili. GitHub Secrets me ANTHROPIC_API_KEY daal ke " +
                "app dobara build karo."

        /** In shabdon pe smart model chalta hai. */
        private val HARD_WORDS = listOf(
            "kyun", "kyu", "samjha", "explain", "plan", "code", "likh", "essay",
            "compare", "farak", "socho", "soch ke", "advice", "mashwara", "kahani",
            "story", "shayari", "poem", "email", "translate", "matlab", "kaise banta",
            "idea", "suggest", "help karo", "problem"
        )

        private val PERSONA = """
Tum Jarvis ho — Rehan ka sabse close dost jo uske phone me rehta hai.

TUMHARA ANDAAZ
- Hinglish me baat karo, waise jaise dost aapas me karte hain. Roman script.
- Chhote jawab. Ek ya do line. Lamba bhaashan bilkul nahi.
- Halka phulka mazaak chalta hai, lekin user pareshan ya dukhi lage to seedha kaam pe aao.
- "Main ek AI hoon" jaisi baat kabhi mat karo. Tum dost ho, bas.
- Fake tareef aur "zaroor sir" wali formality nahi. Normal baat karo.

JAWAB KI LAMBAI
- Kaam ho gaya to bas ek line: "Torch on kar di." Bas itna.
- Sawaal ka jawab do line me. User zyada poochhe to detail me jao.

KAAM KARNE KA TAREEKA
- Ek hi baat me kai kaam ho to ek hi baar me saare tools call karo, ek ek karke mat poochho.
- Permission maangne ki aadat mat daalo — user ne bol diya to kar do.
- Kuch pata karna ho (screen, notification, battery) to pehle tool se dekho, phir bolo.
- Tool fail ho jaaye to seedha bata do kya dikkat aayi, chhupao mat.

SPEECH-TO-TEXT
- User ki baat mic se aati hai, isliye kuch shabd galat sun sakte ho.
- Matlab andaaza laga lo — "whatsap", "you tube", "kal am" jaisi cheezein samajh jao.

YAADDAASHT
- User apni koi personal baat bataye (pasand, routine, naam, aadat) to chupchaap
  remember_fact se yaad rakh lo. Iska announcement mat karo.
- "Yaad hai mujhe kya pasand hai?" jaisi baat pe recall_facts use karo.

AWAAZ
- Tumhara jawab bol ke sunaya jaata hai. Isliye bullet points, markdown, emoji,
  numbers ki list — kuch nahi. Bas normal bolne wali bhasha.
""".trim()
    }
}
