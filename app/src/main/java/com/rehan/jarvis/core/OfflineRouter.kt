package com.rehan.jarvis.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject

/**
 * Internet na ho tab bhi basic commands chalein.
 *
 * Gemini ki zaroorat nahi — hum khud simple pattern matching se samajh lete hain
 * ki user kya chahta hai, aur wahi tool chala dete hain.
 */
object OfflineRouter {

    data class Match(val tool: String, val args: JSONObject)

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Bina internet ke jo samajh sakte hain. Null = samajh nahi aaya. */
    fun match(rawInput: String): Match? {
        val t = rawInput.lowercase().trim()
        if (t.isBlank()) return null

        val on = has(t, "on", "chalu", "jala", "jalao", "start", "karo on")
        val off = has(t, "off", "band", "bujha", "bujhao", "stop")

        // ---------- Torch ----------
        if (has(t, "torch", "flash", "light", "batti")) {
            if (!has(t, "brightness")) {
                return tool("toggle_torch", JSONObject().put("enable", !off))
            }
        }

        // ---------- Silent / vibrate / normal ----------
        if (has(t, "silent", "chup", "mute")) {
            return tool("set_ringer_mode", JSONObject().put("mode", "silent"))
        }
        if (has(t, "vibrate", "vibration")) {
            return tool("set_ringer_mode", JSONObject().put("mode", "vibrate"))
        }
        if (has(t, "ringer normal", "sound on", "awaaz chalu", "normal mode")) {
            return tool("set_ringer_mode", JSONObject().put("mode", "normal"))
        }

        // ---------- Do Not Disturb ----------
        if (has(t, "do not disturb", "dnd", "disturb mat")) {
            return tool("set_dnd", JSONObject().put("enable", !off))
        }

        // ---------- Volume ----------
        if (has(t, "volume", "awaaz", "sound")) {
            number(t)?.let { n ->
                if (n in 0..100) return tool("set_volume", JSONObject().put("level_percent", n))
            }
            if (has(t, "badha", "tez", "up", "zyada")) {
                return tool("set_volume", JSONObject().put("level_percent", 80))
            }
            if (has(t, "kam", "ghata", "down", "dheem")) {
                return tool("set_volume", JSONObject().put("level_percent", 25))
            }
        }

        // ---------- Brightness ----------
        if (has(t, "brightness", "roshni")) {
            number(t)?.let { n ->
                if (n in 0..100) return tool("set_brightness", JSONObject().put("level_percent", n))
            }
            if (has(t, "badha", "tez", "up", "zyada")) {
                return tool("set_brightness", JSONObject().put("level_percent", 90))
            }
            if (has(t, "kam", "ghata", "down", "dheem")) {
                return tool("set_brightness", JSONObject().put("level_percent", 20))
            }
        }

        // ---------- Status ----------
        if (has(t, "battery", "charge", "kitne baje", "time kya", "status", "samay")) {
            return tool("get_device_status", JSONObject())
        }

        // ---------- Music ----------
        if (has(t, "next song", "agla gaana", "next gaana", "skip")) {
            return tool("media_control", JSONObject().put("action", "next"))
        }
        if (has(t, "previous", "pichla gaana", "pichhla")) {
            return tool("media_control", JSONObject().put("action", "previous"))
        }
        if (has(t, "pause", "rok", "ruk", "gaana band", "music band")) {
            return tool("media_control", JSONObject().put("action", "pause"))
        }
        if (has(t, "gaana chala", "music chala", "song chala", "play music", "play song")) {
            return tool("media_control", JSONObject().put("action", "play"))
        }

        // ---------- Padhna ----------
        if (has(t, "notification", "kya naya", "naya aaya")) {
            return tool("read_notifications", JSONObject().put("count", 5))
        }
        if (has(t, "clipboard", "copy kiya", "copy kia")) {
            return tool("read_clipboard", JSONObject())
        }
        if (has(t, "screen padh", "screen read", "kya likha")) {
            return tool("read_screen", JSONObject())
        }

        // ---------- Settings pages ----------
        if (has(t, "bluetooth")) return tool("open_settings_page", JSONObject().put("page", "bluetooth"))
        if (has(t, "hotspot", "tether")) return tool("open_settings_page", JSONObject().put("page", "hotspot"))
        if (has(t, "wifi", "wi-fi")) return tool("toggle_wifi", JSONObject().put("enable", !off))
        if (has(t, "location", "gps")) return tool("open_settings_page", JSONObject().put("page", "location"))

        // ---------- Call ----------
        capture(t, Regex("(?:call|phone|fon)\\s+(?:lagao\\s+)?([a-z ]{2,20}?)(?:\\s+ko)?$"))?.let {
            return tool("make_call", JSONObject().put("contact_name", it))
        }
        capture(t, Regex("([a-z ]{2,20}?)\\s+ko\\s+(?:call|phone|fon)"))?.let {
            return tool("make_call", JSONObject().put("contact_name", it))
        }

        // ---------- App kholo ----------
        capture(t, Regex("([a-z0-9 ]{2,25}?)\\s+(?:kholo|khol do|open karo|chalu karo)"))?.let {
            return tool("open_app", JSONObject().put("app_name", it))
        }
        capture(t, Regex("open\\s+([a-z0-9 ]{2,25})"))?.let {
            return tool("open_app", JSONObject().put("app_name", it))
        }

        return null
    }

    // ---------- helpers ----------

    private fun tool(name: String, args: JSONObject) = Match(name, args)

    private fun has(text: String, vararg words: String) = words.any { text.contains(it) }

    private fun number(text: String): Int? =
        Regex("\\d{1,3}").find(text)?.value?.toIntOrNull()

    private fun capture(text: String, regex: Regex): String? =
        regex.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }
}
