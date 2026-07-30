package com.rehan.jarvis.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class Contact(val name: String, val number: String)

/**
 * Bola hua naam ("mummy", "rahul bhai") se contact number dhoondhta hai.
 * STT galat spelling deta hai isliye fuzzy matching zaroori hai.
 */
object ContactResolver {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun resolve(context: Context, spokenName: String): Contact? {
        if (!hasPermission(context)) return null
        val query = normalize(spokenName)
        if (query.isBlank()) return null

        val all = loadAll(context)
        if (all.isEmpty()) return null

        // 1. Exact match
        all.firstOrNull { normalize(it.name) == query }?.let { return it }

        // 2. Contains match (chhota naam bade naam ke andar)
        all.firstOrNull { normalize(it.name).contains(query) }?.let { return it }
        all.firstOrNull { query.contains(normalize(it.name)) }?.let { return it }

        // 3. First-word match ("rahul bhai" -> "Rahul Sharma")
        val firstWord = query.split(" ").firstOrNull().orEmpty()
        if (firstWord.length >= 3) {
            all.firstOrNull { normalize(it.name).startsWith(firstWord) }?.let { return it }
        }

        // 4. Fuzzy — sabse kam edit distance, agar kaafi close ho
        return all
            .map { it to levenshtein(normalize(it.name), query) }
            .minByOrNull { it.second }
            ?.takeIf { (contact, dist) -> dist <= maxOf(2, contact.name.length / 3) }
            ?.first
    }

    private fun loadAll(context: Context): List<Contact> {
        val out = mutableListOf<Contact>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        ) ?: return out

        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seen = mutableSetOf<String>()
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numIdx) ?: continue
                if (seen.add(normalize(name))) out.add(Contact(name, cleanNumber(number)))
            }
        }
        return out
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()

    fun cleanNumber(number: String) = number.replace(Regex("[^+0-9]"), "")

    /** WhatsApp ko country code chahiye. */
    fun toWhatsAppNumber(number: String, defaultCountryCode: String = "91"): String {
        val digits = number.replace(Regex("[^0-9]"), "")
        return when {
            number.startsWith("+") -> digits
            digits.length == 10 -> defaultCountryCode + digits
            else -> digits
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
