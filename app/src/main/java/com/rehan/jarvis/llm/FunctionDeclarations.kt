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
            put(
                fn(
                    "open_app", "Phone me koi app kholo",
                    mapOf("app_name" to str("App ka naam jaise 'YouTube', 'Instagram', 'Settings'")),
                    listOf("app_name")
                )
            )
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
                    "get_device_status", "Battery, volume, WiFi aur time ki jaankari lo",
                    emptyMap(), emptyList()
                )
            )
        }
    }
}
