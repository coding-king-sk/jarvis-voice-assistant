package com.rehan.jarvis.memory

import android.content.Context
import org.json.JSONArray

/**
 * Jarvis ki yaaddaasht.
 *
 * Chhoti chhoti baatein phone me hi save hoti hain (koi server nahi):
 * "mujhe chai pasand hai", "mummy ka number pe raat me call mat karna",
 * "main subah 6 baje uthta hoon". Har baat-cheet me ye Claude ko bheji jaati hain,
 * isliye usse baar baar batana nahi padta.
 */
object MemoryStore {

    private const val PREFS = "jarvis"
    private const val KEY = "memory_facts"
    private const val MAX_FACTS = 60

    fun add(context: Context, fact: String): String {
        val clean = fact.trim()
        if (clean.length < 3) return "Ye baat samajh nahi aayi, dobara bolo."

        val facts = all(context).toMutableList()
        if (facts.any { it.equals(clean, ignoreCase = true) }) {
            return "Ye to mujhe pehle se yaad hai."
        }

        facts.add(clean)
        while (facts.size > MAX_FACTS) facts.removeAt(0)
        save(context, facts)
        return "Yaad rakh liya."
    }

    fun all(context: Context): List<String> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Bolne ke liye — jab user poochhe "tujhe mere baare me kya yaad hai". */
    fun recall(context: Context): String {
        val facts = all(context)
        if (facts.isEmpty()) return "Abhi tumhare baare me kuch yaad nahi hai."
        return "Ye yaad hai: " + facts.joinToString(". ")
    }

    /** System prompt me chipkane ke liye. */
    fun promptBlock(context: Context): String {
        val facts = all(context)
        if (facts.isEmpty()) return ""
        return "\n\nTUMHE YE BAATEIN YAAD HAIN (user ne khud batayi thi):\n" +
            facts.joinToString("\n") { "- $it" }
    }

    fun forget(context: Context, about: String): String {
        val key = about.trim().lowercase()
        if (key.isBlank()) return clear(context)

        val facts = all(context)
        val kept = facts.filterNot { it.lowercase().contains(key) }
        if (kept.size == facts.size) return "Is baare me kuch yaad hi nahi tha."

        save(context, kept)
        return "Theek hai, bhool gaya."
    }

    fun clear(context: Context): String {
        save(context, emptyList())
        return "Sab kuch bhool gaya, ekdum saaf."
    }

    private fun save(context: Context, facts: List<String>) {
        val array = JSONArray()
        facts.forEach { array.put(it) }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
