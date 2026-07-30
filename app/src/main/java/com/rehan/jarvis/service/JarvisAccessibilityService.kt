package com.rehan.jarvis.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Screen pe jo likha hai use padhne ke liye.
 * "Screen padho" bolne pe ye current app ka saara text nikaal deta hai.
 *
 * User ko ise manually on karna padta hai:
 * Settings > Accessibility > Jarvis
 */
class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Hum sirf on-demand padhte hain, har event pe kuch nahi karte.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: JarvisAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        /** Screen ka text nikaalo. Null matlab service band hai. */
        fun readScreen(): String? {
            val service = instance ?: return null
            val root = service.rootInActiveWindow ?: return ""
            val out = mutableListOf<String>()
            collect(root, out)
            return out.distinct().joinToString(". ").take(2000)
        }

        private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>) {
            if (node == null || out.size >= 80) return

            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                out.add(text)
            } else {
                val desc = node.contentDescription?.toString()?.trim()
                if (!desc.isNullOrBlank()) out.add(desc)
            }

            for (i in 0 until node.childCount) {
                collect(node.getChild(i), out)
            }
        }
    }
}
