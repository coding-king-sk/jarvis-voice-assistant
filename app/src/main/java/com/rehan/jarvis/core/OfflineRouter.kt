package com.rehan.jarvis.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Offline samajhne ka nateeja: ya to koi tool chalao, ya seedha jawab bol do. */
sealed interface OfflineResult {
    data class Tool(val tool: String, val args: JSONObject) : OfflineResult
    data class Speak(val text: String) : OfflineResult
}

/**
 * Bina internet ke bhi Jarvis kaam kare.
 *
 * Yahan koi AI model nahi hai — sirf saaf-suthri Hinglish pattern matching.
 * Isliye ye 0 MB leta hai, 0 battery kharch karta hai, aur turant jawab deta hai.
 */
object OfflineRouter {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * "Torch on karo aur phone silent kar do" ko do alag commands me todo.
     *
     * Message wale commands ko kabhi nahi todte, warna
     * "papa ko sms karo ki main aa raha hoon aur khana laaunga" adha kat jaayega.
     */
    fun split(rawInput: String): List<String> {
        val t = rawInput.trim()
        if (t.isBlank()) return emptyList()

        val lower = t.lowercase()
        val isMessage = MESSAGE_WORDS.any { lower.contains(it) }
        if (isMessage) return listOf(t)

        val parts = t.split(SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.length > 2 }

        return if (parts.isEmpty()) listOf(t) else parts
    }

    /** Null = samajh nahi aaya. */
    fun match(rawInput: String): OfflineResult? {
        val t = normalize(rawInput)
        if (t.isBlank()) return null

        val off = has(t, "off", "band", "bujha", "bujhao", "stop", "hata")

        greeting(t)?.let { return it }
        clock(t)?.let { return it }
        math(t)?.let { return it }
        system(t)?.let { return it }
        alarm(t)?.let { return it }
        reminder(t)?.let { return it }
        messaging(t)?.let { return it }
        media(t)?.let { return it }
        reading(t)?.let { return it }
        device(t, off)?.let { return it }
        calling(t)?.let { return it }
        appOpen(t)?.let { return it }

        return null
    }

    // ---------- Greetings aur chhoti baatein ----------

    private fun greeting(t: String): OfflineResult? = when {
        has(t, "namaste", "hello", "hi jarvis", "hey jarvis") ->
            speak("Namaste! Boliye, kya karna hai?")

        has(t, "kaise ho", "kaisa hai", "how are you") ->
            speak("Main bilkul theek hoon, aap bataiye.")

        has(t, "tumhara naam", "tum kaun", "who are you") ->
            speak("Main Jarvis hoon, aapka phone assistant.")

        has(t, "thank", "shukriya", "dhanyavad") ->
            speak("Koi baat nahi.")

        has(t, "good night", "shubh ratri") ->
            speak("Good night! Aaram se soiye.")

        has(t, "kya kar sakte ho", "kya kar sakta hai", "help", "madad") ->
            speak(
                "Main torch, volume, brightness, silent mode, alarm, reminder, " +
                    "call, message, music, screenshot aur notifications sambhal sakta hoon. " +
                    "Ye sab bina internet ke bhi chalta hai."
            )

        else -> null
    }

    // ---------- Time aur date ----------

    private fun clock(t: String): OfflineResult? {
        val now = System.currentTimeMillis()

        if (has(t, "kitne baje", "time kya", "kya time", "samay kya", "what time")) {
            val time = SimpleDateFormat("h:mm a", Locale.US).format(now)
            return speak("Abhi $time baje hain.")
        }

        if (has(t, "aaj ki tarikh", "date kya", "kaunsa din", "aaj kya din", "what date")) {
            val date = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(now)
            return speak("Aaj $date hai.")
        }

        if (has(t, "kal ki tarikh", "kal kya din")) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val date = SimpleDateFormat("EEEE, d MMMM", Locale.US).format(cal.time)
            return speak("Kal $date hai.")
        }

        return null
    }

    // ---------- Chhota sa calculator ----------

    private fun math(t: String): OfflineResult? {
        val m = MATH_REGEX.find(t) ?: return null
        val a = m.groupValues[1].toDoubleOrNull() ?: return null
        val b = m.groupValues[3].toDoubleOrNull() ?: return null

        val result = when (m.groupValues[2].trim()) {
            "plus", "jama", "+", "add" -> a + b
            "minus", "ghata", "-" -> a - b
            "into", "guna", "x", "*", "times", "multiplied by" -> a * b
            "divided by", "batta", "/", "bhag" ->
                if (b == 0.0) return speak("Zero se bhag nahi hota.") else a / b
            else -> return null
        }

        val pretty = if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", result)
        }
        return speak("Jawab hai $pretty.")
    }

    // ---------- Screenshot, camera, navigation ----------

    private fun system(t: String): OfflineResult? = when {
        has(t, "screenshot", "screen shot", "screen capture") ->
            tool("take_screenshot", JSONObject())

        has(t, "photo lo", "photo le", "selfie", "tasveer", "take photo", "camera kholo") ->
            tool("take_photo", JSONObject())

        has(t, "home jao", "home button", "go home") ->
            tool("press_key", JSONObject().put("key", "home"))

        has(t, "back jao", "peeche jao", "go back") ->
            tool("press_key", JSONObject().put("key", "back"))

        has(t, "recent apps", "recents") ->
            tool("press_key", JSONObject().put("key", "recents"))

        has(t, "notification panel", "notification kholo") ->
            tool("press_key", JSONObject().put("key", "notifications"))

        has(t, "quick settings") ->
            tool("press_key", JSONObject().put("key", "quick_settings"))

        has(t, "phone lock", "screen lock", "lock kar") ->
            tool("press_key", JSONObject().put("key", "lock"))

        else -> null
    }

    // ---------- Alarm ----------

    private fun alarm(t: String): OfflineResult? {
        if (!has(t, "alarm", "jaga dena", "uthana")) return null

        val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?").find(t) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null

        val evening = has(t, "shaam", "raat", "pm", "evening", "night", "dopahar")
        val morning = has(t, "subah", "morning", "am", "savere")

        if (evening && hour in 1..11) hour += 12
        if (morning && hour == 12) hour = 0

        return tool(
            "set_alarm",
            JSONObject()
                .put("hour", hour)
                .put("minute", minute)
                .put("label", "Jarvis alarm")
        )
    }

    // ---------- Reminder / timer ----------

    private fun reminder(t: String): OfflineResult? {
        if (!has(t, "yaad dila", "reminder", "timer", "remind")) return null

        val m = Regex("(\\d{1,3})\\s*(second|sec|minute|min|mint|ghante|ghanta|hour)").find(t)
        val minutes = when {
            m == null -> 5
            m.groupValues[2].startsWith("h") || m.groupValues[2].startsWith("gh") ->
                (m.groupValues[1].toIntOrNull() ?: 1) * 60
            m.groupValues[2].startsWith("s") -> 1
            else -> m.groupValues[1].toIntOrNull() ?: 5
        }

        val text = t.substringAfter(" ki ", "").trim().ifBlank { "Jarvis reminder" }

        return tool(
            "set_reminder",
            JSONObject().put("text", text).put("minutes_from_now", minutes)
        )
    }

    // ---------- Message bhejna ----------

    private fun messaging(t: String): OfflineResult? {
        WHATSAPP_REGEX.find(t)?.let { m ->
            val name = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            if (name.length >= 2 && body.isNotBlank()) {
                return tool(
                    "send_whatsapp",
                    JSONObject().put("contact_name", name).put("message", body)
                )
            }
        }

        SMS_REGEX.find(t)?.let { m ->
            val name = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            if (name.length >= 2 && body.isNotBlank()) {
                return tool(
                    "send_sms",
                    JSONObject().put("contact_name", name).put("message", body)
                )
            }
        }

        return null
    }

    // ---------- Music ----------

    private fun media(t: String): OfflineResult? = when {
        has(t, "next song", "agla gaana", "next gaana", "skip", "aage badha") ->
            tool("media_control", JSONObject().put("action", "next"))

        has(t, "previous", "pichla gaana", "pichhla", "peeche wala") ->
            tool("media_control", JSONObject().put("action", "previous"))

        has(t, "pause", "gaana rok", "music rok", "gaana band", "music band") ->
            tool("media_control", JSONObject().put("action", "pause"))

        has(t, "youtube") && has(t, "song", "gaana", "play", "chala") ->
            tool("play_on_youtube", JSONObject().put("query", cleanQuery(t)))

        has(t, "gaana chala", "music chala", "song chala", "play music", "play song") -> {
            val query = cleanQuery(t)
            if (query.length > 3) {
                tool("play_music", JSONObject().put("query", query))
            } else {
                tool("media_control", JSONObject().put("action", "play"))
            }
        }

        else -> null
    }

    /** "youtube kholo koi bhi gaana chalao" me se bacha hua naam nikaalo. */
    private fun cleanQuery(t: String): String {
        var q = t
        NOISE_WORDS.forEach { q = q.replace(it, " ") }
        return q.replace(Regex("\\s+"), " ").trim()
    }

    // ---------- Padhne wale kaam ----------

    private fun reading(t: String): OfflineResult? = when {
        has(t, "notification", "kya naya", "naya aaya", "message aaya") ->
            tool("read_notifications", JSONObject().put("count", 5))

        has(t, "clipboard", "copy kiya", "copy kia") ->
            tool("read_clipboard", JSONObject())

        has(t, "screen padh", "screen read", "kya likha") ->
            tool("read_screen", JSONObject())

        has(t, "reply karo", "jawab do", "reply kar do") -> {
            val body = t.substringAfter("reply karo", "")
                .substringAfter("jawab do", "")
                .removePrefix(" ki ")
                .removePrefix(":")
                .trim()
            if (body.isNotBlank()) {
                tool("reply_last_message", JSONObject().put("message", body))
            } else {
                speak("Reply me kya likhna hai wo bhi bata dijiye.")
            }
        }

        else -> null
    }

    // ---------- Device controls ----------

    private fun device(t: String, off: Boolean): OfflineResult? {
        if (has(t, "torch", "flash", "batti") ||
            (has(t, "light") && !has(t, "brightness"))
        ) {
            return tool("toggle_torch", JSONObject().put("enable", !off))
        }

        if (has(t, "silent", "chup", "mute")) {
            return tool("set_ringer_mode", JSONObject().put("mode", "silent"))
        }
        if (has(t, "vibrate", "vibration")) {
            return tool("set_ringer_mode", JSONObject().put("mode", "vibrate"))
        }
        if (has(t, "ringer normal", "sound on", "awaaz chalu", "normal mode", "unmute")) {
            return tool("set_ringer_mode", JSONObject().put("mode", "normal"))
        }
        if (has(t, "do not disturb", "dnd", "disturb mat")) {
            return tool("set_dnd", JSONObject().put("enable", !off))
        }

        if (has(t, "volume", "awaaz", "sound")) {
            percent(t)?.let { return tool("set_volume", JSONObject().put("level_percent", it)) }
            if (has(t, "badha", "tez", "up", "zyada", "full")) {
                return tool(
                    "set_volume",
                    JSONObject().put("level_percent", if (has(t, "full")) 100 else 80)
                )
            }
            if (has(t, "kam", "ghata", "down", "dheem")) {
                return tool("set_volume", JSONObject().put("level_percent", 25))
            }
        }

        if (has(t, "brightness", "roshni")) {
            percent(t)?.let { return tool("set_brightness", JSONObject().put("level_percent", it)) }
            if (has(t, "badha", "tez", "up", "zyada", "full")) {
                return tool("set_brightness", JSONObject().put("level_percent", 90))
            }
            if (has(t, "kam", "ghata", "down", "dheem")) {
                return tool("set_brightness", JSONObject().put("level_percent", 20))
            }
        }

        if (has(t, "battery", "charge", "status", "kitni bachi")) {
            return tool("get_device_status", JSONObject())
        }

        if (has(t, "bluetooth")) return tool("open_settings_page", JSONObject().put("page", "bluetooth"))
        if (has(t, "hotspot", "tether")) return tool("open_settings_page", JSONObject().put("page", "hotspot"))
        if (has(t, "wifi", "wi fi")) return tool("toggle_wifi", JSONObject().put("enable", !off))
        if (has(t, "location", "gps")) return tool("open_settings_page", JSONObject().put("page", "location"))
        if (has(t, "mobile data", "internet chalu")) {
            return tool("open_settings_page", JSONObject().put("page", "mobile_data"))
        }

        return null
    }

    // ---------- Call ----------

    private fun calling(t: String): OfflineResult? {
        CALL_REGEX_1.find(t)?.groupValues?.getOrNull(1)?.trim()?.let {
            if (it.length >= 2) return tool("make_call", JSONObject().put("contact_name", it))
        }
        CALL_REGEX_2.find(t)?.groupValues?.getOrNull(1)?.trim()?.let {
            if (it.length >= 2) return tool("make_call", JSONObject().put("contact_name", it))
        }
        return null
    }

    // ---------- App kholo ----------

    private fun appOpen(t: String): OfflineResult? {
        OPEN_REGEX_1.find(t)?.groupValues?.getOrNull(1)?.trim()?.let {
            if (it.length >= 2) return tool("open_app", JSONObject().put("app_name", it))
        }
        OPEN_REGEX_2.find(t)?.groupValues?.getOrNull(1)?.trim()?.let {
            if (it.length >= 2) return tool("open_app", JSONObject().put("app_name", it))
        }
        return null
    }

    // ---------- Helpers ----------

    private fun tool(name: String, args: JSONObject): OfflineResult =
        OfflineResult.Tool(name, args)

    private fun speak(text: String): OfflineResult = OfflineResult.Speak(text)

    private fun has(text: String, vararg words: String) = words.any { text.contains(it) }

    private fun percent(text: String): Int? =
        Regex("\\d{1,3}").find(text)?.value?.toIntOrNull()?.takeIf { it in 0..100 }

    /**
     * Bolne me log "paanch" bolte hain, "5" nahi.
     * Isliye pehle shabdon ko numbers me badal dete hain.
     */
    private fun normalize(raw: String): String {
        var t = raw.lowercase().trim()
        NUMBER_WORDS.forEach { (word, value) ->
            t = t.replace(Regex("\\b$word\\b"), value.toString())
        }
        return t
    }

    private val SPLIT_REGEX = Regex(
        "\\s+(?:aur|and|phir|then|uske baad|iske baad)\\s+|\\s*,\\s*",
        RegexOption.IGNORE_CASE
    )

    private val MESSAGE_WORDS = listOf(
        "whatsapp", "sms", "message", "msg", "reply", "jawab do", "bhejo"
    )

    private val NOISE_WORDS = listOf(
        "youtube", "kholo", "khol do", "open", "koi bhi", "koi", "gaana", "gana",
        "song", "music", "chalao", "chala do", "chala", "play", "karo", "kar do", "do"
    )

    private val NUMBER_WORDS: Map<String, Int> = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "tin" to 3, "char" to 4, "chaar" to 4,
        "paanch" to 5, "panch" to 5, "chhe" to 6, "che" to 6, "saat" to 7,
        "aath" to 8, "nau" to 9, "das" to 10, "gyarah" to 11, "barah" to 12,
        "pandrah" to 15, "bees" to 20, "pachees" to 25, "tees" to 30,
        "chalis" to 40, "pachas" to 50, "saath" to 60, "sattar" to 70,
        "assi" to 80, "nabbe" to 90, "sau" to 100,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "twenty" to 20, "thirty" to 30, "fifty" to 50, "hundred" to 100
    )

    private val MATH_REGEX = Regex(
        "(-?\\d+(?:\\.\\d+)?)\\s*" +
            "(plus|jama|add|\\+|minus|ghata|-|into|guna|times|multiplied by|x|\\*|" +
            "divided by|batta|bhag|/)" +
            "\\s*(-?\\d+(?:\\.\\d+)?)"
    )

    private val WHATSAPP_REGEX = Regex(
        "([a-z ]{2,20}?)\\s+ko\\s+whatsapp\\s*(?:karo|kar do|bhejo|message)?\\s*(?:ki|:)?\\s*(.+)"
    )

    private val SMS_REGEX = Regex(
        "([a-z ]{2,20}?)\\s+ko\\s+(?:sms|message|msg)\\s*(?:karo|kar do|bhejo)?\\s*(?:ki|:)?\\s*(.+)"
    )

    private val CALL_REGEX_1 = Regex("([a-z ]{2,20}?)\\s+ko\\s+(?:call|phone|fon|kal)")
    private val CALL_REGEX_2 = Regex("(?:call|phone|fon)\\s+(?:lagao\\s+)?([a-z ]{2,20})")

    private val OPEN_REGEX_1 = Regex("([a-z0-9 ]{2,25}?)\\s+(?:kholo|khol do|open karo|chalu karo)")
    private val OPEN_REGEX_2 = Regex("open\\s+([a-z0-9 ]{2,25})")
}
