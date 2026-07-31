package com.rehan.jarvis.llm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Claude ko batate hain ki phone pe kya kya kar sakta hai.
 * Anthropic ka format: har tool me name, description aur input_schema hota hai.
 */
object ClaudeTools {

    val all: JSONArray by lazy { build() }

    private fun build(): JSONArray {
        val tools = JSONArray()

        tools.put(
            tool(
                "make_call", "Kisi contact ko phone call lagao.",
                JSONObject().put("contact_name", str("Contact ka naam, jaise 'mummy' ya 'Rohit'")),
                listOf("contact_name")
            )
        )
        tools.put(
            tool(
                "send_whatsapp", "Kisi contact ko WhatsApp message bhejo.",
                JSONObject()
                    .put("contact_name", str("Contact ka naam"))
                    .put("message", str("Message ka text")),
                listOf("contact_name", "message")
            )
        )
        tools.put(
            tool(
                "send_sms", "Kisi contact ko SMS bhejo.",
                JSONObject()
                    .put("contact_name", str("Contact ka naam"))
                    .put("message", str("Message ka text")),
                listOf("contact_name", "message")
            )
        )
        tools.put(
            tool(
                "open_app", "Phone me koi app kholo.",
                JSONObject().put("app_name", str("App ka naam, jaise 'Instagram'")),
                listOf("app_name")
            )
        )
        tools.put(
            tool(
                "take_screenshot", "Screen ka screenshot lo.",
                JSONObject()
            )
        )
        tools.put(
            tool(
                "press_key", "Phone ka koi system button dabao.",
                JSONObject().put(
                    "key",
                    enumStr(
                        "Kaunsa button",
                        listOf("home", "back", "recents", "notifications", "quick_settings", "lock")
                    )
                ),
                listOf("key")
            )
        )
        tools.put(
            tool(
                "take_photo",
                "Camera se khud photo kheencho aur usme kya hai wo dekho. " +
                    "User apni photo ya selfie maange to camera 'front' rakho.",
                JSONObject()
                    .put("camera", enumStr("Kaunsa camera", listOf("back", "front")))
                    .put("question", str("Photo me kya dekhna hai, jaise 'ye kaunsa phool hai'"))
            )
        )
        tools.put(
            tool(
                "set_alarm", "Alarm lagao.",
                JSONObject()
                    .put("hour", num("Ghanta, 24 hour format me (0-23)"))
                    .put("minute", num("Minute (0-59)"))
                    .put("label", str("Alarm ka naam")),
                listOf("hour", "minute")
            )
        )
        tools.put(
            tool(
                "set_reminder", "Kuch minute baad yaad dilane ke liye reminder lagao.",
                JSONObject()
                    .put("text", str("Kya yaad dilana hai"))
                    .put("minutes_from_now", num("Kitne minute baad")),
                listOf("text", "minutes_from_now")
            )
        )
        tools.put(
            tool(
                "set_volume", "Media volume set karo.",
                JSONObject().put("level_percent", num("0 se 100")),
                listOf("level_percent")
            )
        )
        tools.put(
            tool(
                "set_brightness", "Screen ki brightness set karo.",
                JSONObject().put("level_percent", num("0 se 100")),
                listOf("level_percent")
            )
        )
        tools.put(
            tool(
                "toggle_wifi", "WiFi ka panel kholo (Android app se seedha on/off nahi hone deta).",
                JSONObject().put("enable", bool("true = on karna hai")),
                listOf("enable")
            )
        )
        tools.put(
            tool(
                "toggle_torch", "Flashlight on ya off karo.",
                JSONObject().put("enable", bool("true = on")),
                listOf("enable")
            )
        )
        tools.put(
            tool(
                "set_ringer_mode", "Phone ko silent, vibrate ya normal karo.",
                JSONObject().put(
                    "mode",
                    enumStr("Mode", listOf("silent", "vibrate", "normal"))
                ),
                listOf("mode")
            )
        )
        tools.put(
            tool(
                "set_dnd", "Do Not Disturb on ya off karo.",
                JSONObject().put("enable", bool("true = on")),
                listOf("enable")
            )
        )
        tools.put(
            tool(
                "open_settings_page", "Kisi settings page pe le jao.",
                JSONObject().put(
                    "page",
                    enumStr(
                        "Kaunsa page",
                        listOf(
                            "bluetooth", "hotspot", "location", "battery",
                            "mobile_data", "display", "sound"
                        )
                    )
                ),
                listOf("page")
            )
        )
        tools.put(
            tool(
                "get_device_status",
                "Battery, volume, ringer mode aur time ek saath pata karo.",
                JSONObject()
            )
        )
        tools.put(
            tool("read_clipboard", "Clipboard me copy kiya hua text padho.", JSONObject())
        )
        tools.put(
            tool(
                "read_screen",
                "Screen pe abhi jo dikh raha hai wo text padho. Tab use karo jab user " +
                    "poochhe 'ye kya likha hai' ya screen ke baare me kuch poochhe.",
                JSONObject()
            )
        )
        tools.put(
            tool(
                "read_notifications", "Recent notifications padho.",
                JSONObject().put("count", num("Kitni notifications (1-10)"))
            )
        )
        tools.put(
            tool(
                "reply_last_message", "Aakhri aaye message ka reply bhejo.",
                JSONObject().put("message", str("Reply ka text")),
                listOf("message")
            )
        )
        tools.put(
            tool(
                "media_control", "Jo gaana chal raha hai use control karo.",
                JSONObject().put(
                    "action",
                    enumStr("Kya karna hai", listOf("play", "pause", "next", "previous"))
                ),
                listOf("action")
            )
        )
        tools.put(
            tool(
                "play_music", "Koi gaana ya artist bajao.",
                JSONObject().put("query", str("Gaane ka naam ya artist"))
            )
        )
        tools.put(
            tool(
                "play_on_youtube",
                "YouTube pe search karke pehla video seedha chala do. " +
                    "'YouTube pe gaana chalao' jaisi baat pe yahi use karo.",
                JSONObject().put("query", str("Kya dhoondhna hai"))
            )
        )

        // ---------- Yaaddaasht ----------
        tools.put(
            tool(
                "remember_fact",
                "User ke baare me koi kaam ki baat hamesha ke liye yaad rakho — pasand, " +
                    "routine, naam, aadat. Jab user kahe 'yaad rakhna' ya koi personal " +
                    "baat bataye tab chupchaap ye use karo.",
                JSONObject().put("fact", str("Ek line me wo baat")),
                listOf("fact")
            )
        )
        tools.put(
            tool(
                "recall_facts",
                "User ke baare me jo yaad hai wo sab padho.",
                JSONObject()
            )
        )
        tools.put(
            tool(
                "forget_memory",
                "Yaad ki hui baatein bhool jao. 'about' do to sirf usse judi baatein hatengi.",
                JSONObject().put("about", str("Kis baare me bhoolna hai, khaali = sab kuch"))
            )
        )

        return tools
    }

    // ---------- chhote helpers ----------

    private fun tool(
        name: String,
        description: String,
        properties: JSONObject,
        required: List<String> = emptyList()
    ): JSONObject {
        val schema = JSONObject()
            .put("type", "object")
            .put("properties", properties)
        if (required.isNotEmpty()) {
            val array = JSONArray()
            required.forEach { array.put(it) }
            schema.put("required", array)
        }
        return JSONObject()
            .put("name", name)
            .put("description", description)
            .put("input_schema", schema)
    }

    private fun str(description: String) =
        JSONObject().put("type", "string").put("description", description)

    private fun num(description: String) =
        JSONObject().put("type", "integer").put("description", description)

    private fun bool(description: String) =
        JSONObject().put("type", "boolean").put("description", description)

    private fun enumStr(description: String, values: List<String>): JSONObject {
        val array = JSONArray()
        values.forEach { array.put(it) }
        return JSONObject()
            .put("type", "string")
            .put("description", description)
            .put("enum", array)
    }
}
