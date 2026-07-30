package com.rehan.jarvis.llm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini ko batate hain ki hum kaunse kaam kar sakte hain.
 * Gemini khud decide karta hai kaunsa function call karna hai.
 */
object FunctionDeclarations {

    private fun str(desc: String) = JSONObject().put("type", "STRING").put("description", desc)
    private fun int(desc: String) = JSONObject().put("type", "INTEGER").put("description", desc)
    private fun bool(desc: String) = JSONObject().put("type", "BOOLEAN").put("description", desc)

    private fun enumStr(desc: String, values: List<String>) = JSONObject()
        .put("type", "STRING")
        .put("description", desc)
        .put("enum", JSONArray(values))

    private fun fn(
        name: String,
        description: String,
        props: Map<String, JSONObject>,
        required: List<String>
    ): JSONObject {
        val properties = JSONObject()
        props.forEach { (k, v) -> properties.put(k, v) }
        val params = JSONObject()
            .put("type", "OBJECT")
            .put("properties", properties)
            .put("required", JSONArray(required))
        return JSONObject()
            .put("name", name)
            .put("description", description)
            .put("parameters", params)
    }

    val all: JSONArray by lazy {
        JSONArray().apply {

            // ---------- Communication ----------
            put(
                fn(
                    "make_call", "Kisi contact ko phone call lagao",
                    mapOf("contact_name" to str("Contact ka naam jaise 'Mummy' ya 'Rahul'")),
                    listOf("contact_name")
                )
            )
            put(
                fn(
                    "send_whatsapp", "Kisi contact ko WhatsApp message bhejo",
                    mapOf(
                        "contact_name" to str("Contact ka naam"),
                        "message" to str("Message ka text")
                    ),
                    listOf("contact_name", "message")
                )
            )
            put(
                fn(
                    "send_sms", "Kisi contact ko SMS bhejo",
                    mapOf(
                        "contact_name" to str("Contact ka naam"),
                        "message" to str("Message ka text")
                    ),
                    listOf("contact_name", "message")
                )
            )

            // ---------- Apps ----------
            put(
                fn(
                    "open_app", "Phone me koi app kholo",
                    mapOf("app_name" to str("App ka naam jaise 'YouTube', 'Instagram', 'Settings'")),
                    listOf("app_name")
                )
            )

            // ---------- Time ----------
            put(
                fn(
                    "set_alarm", "Alarm set karo",
                    mapOf(
                        "hour" to int("Ghanta, 24-hour format (0-23)"),
                        "minute" to int("Minute (0-59)"),
                        "label" to str("Alarm ka naam, optional")
                    ),
                    listOf("hour", "minute")
                )
            )
            put(
                fn(
                    "set_reminder", "Kitni der baad yaad dilana hai",
                    mapOf(
                        "text" to str("Kya yaad dilana hai"),
                        "minutes_from_now" to int("Kitne minute baad")
                    ),
                    listOf("text", "minutes_from_now")
                )
            )

            // ---------- Device ----------
            put(
                fn(
                    "set_volume", "Media volume set karo",
                    mapOf("level_percent" to int("0 se 100 ke beech")),
                    listOf("level_percent")
                )
            )
            put(
                fn(
                    "set_brightness", "Screen brightness set karo",
                    mapOf("level_percent" to int("0 se 100 ke beech")),
                    listOf("level_percent")
                )
            )
            put(
                fn(
                    "toggle_wifi", "WiFi on ya off karo (Android 10+ pe settings panel khulega)",
                    mapOf("enable" to bool("true = on, false = off")),
                    listOf("enable")
                )
            )
            put(
                fn(
                    "toggle_torch", "Flashlight ya torch on/off karo",
                    mapOf("enable" to bool("true = on, false = off")),
                    listOf("enable")
                )
            )
            put(
                fn(
                    "set_ringer_mode", "Phone ko silent, vibrate ya normal karo",
                    mapOf(
                        "mode" to enumStr(
                            "Ringer mode",
                            listOf("silent", "vibrate", "normal")
                        )
                    ),
                    listOf("mode")
                )
            )
            put(
                fn(
                    "set_dnd", "Do Not Disturb on ya off karo",
                    mapOf("enable" to bool("true = on, false = off")),
                    listOf("enable")
                )
            )
            put(
                fn(
                    "open_settings_page",
                    "Bluetooth, hotspot, location jaisi settings ka page kholo",
                    mapOf(
                        "page" to enumStr(
                            "Kaunsi settings",
                            listOf(
                                "bluetooth", "hotspot", "location", "battery",
                                "mobile_data", "display", "sound"
                            )
                        )
                    ),
                    listOf("page")
                )
            )
            put(
                fn(
                    "get_device_status",
                    "Battery, charging, volume, ringer mode aur time ki jaankari lo",
                    emptyMap(), emptyList()
                )
            )

            // ---------- Padhne wale kaam ----------
            put(
                fn(
                    "read_clipboard", "Jo text copy kiya hua hai use padho",
                    emptyMap(), emptyList()
                )
            )
            put(
                fn(
                    "read_screen",
                    "Screen pe abhi jo dikh raha hai wo text padho. " +
                        "Tab use karo jab user kahe 'screen padho' ya 'ye kya likha hai'",
                    emptyMap(), emptyList()
                )
            )
            put(
                fn(
                    "read_notifications",
                    "Naye notifications padho. Tab use karo jab user pooche 'kya naya aaya' " +
                        "ya 'notifications sunao'",
                    mapOf("count" to int("Kitne notifications, default 5")),
                    emptyList()
                )
            )
            put(
                fn(
                    "reply_last_message",
                    "Aakhri aaye message ka reply bhejo, app khole bina",
                    mapOf("message" to str("Reply ka text")),
                    listOf("message")
                )
            )

            // ---------- Music ----------
            put(
                fn(
                    "media_control",
                    "Chal rahe gaane ko play, pause, next ya previous karo",
                    mapOf(
                        "action" to enumStr(
                            "Kya karna hai",
                            listOf("play", "pause", "next", "previous")
                        )
                    ),
                    listOf("action")
                )
            )
            put(
                fn(
                    "play_music",
                    "Koi gaana, artist ya playlist chalao",
                    mapOf("query" to str("Gaane ya artist ka naam")),
                    listOf("query")
                )
            )
        }
    }
}
