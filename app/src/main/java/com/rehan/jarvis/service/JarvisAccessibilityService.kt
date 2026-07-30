package com.rehan.jarvis.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Screen padhne aur phone chalane ke liye.
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
        // Hum sirf on-demand kaam karte hain.
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

        /**
         * Screenshot lo. Android 11+ pe hi possible hai.
         * @return null matlab service band hai
         */
        fun takeScreenshot(): Boolean? {
            val service = instance ?: return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            return service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }

        /** Home, back, recents jaise buttons dabao. */
        fun pressKey(key: String): Boolean? {
            val service = instance ?: return null
            val action = when (key.lowercase().trim()) {
                "home" -> GLOBAL_ACTION_HOME
                "back", "peeche" -> GLOBAL_ACTION_BACK
                "recents", "recent" -> GLOBAL_ACTION_RECENTS
                "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
                "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
                "lock" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN
                    else return false
                else -> return false
            }
            return service.performGlobalAction(action)
        }

        /**
         * YouTube search kholne ke baad pehli video khud tap kar do.
         * Intent se autoplay possible nahi hai, isliye ye chhota sa jugaad hai.
         */
        fun tapFirstYoutubeResult(delayMs: Long = 3500) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val root = service.rootInActiveWindow ?: return@postDelayed
                    for (id in YOUTUBE_THUMBNAIL_IDS) {
                        val nodes = root.findAccessibilityNodeInfosByViewId(id)
                        if (!nodes.isNullOrEmpty()) {
                            clickableParent(nodes[0])
                                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            return@postDelayed
                        }
                    }
                } catch (_: Exception) {
                }
            }, delayMs)
        }

        private fun clickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            var current = node
            var hops = 0
            while (current != null && !current.isClickable && hops < 6) {
                current = current.parent
                hops++
            }
            return current
        }

        private val YOUTUBE_THUMBNAIL_IDS = listOf(
            "com.google.android.youtube:id/thumbnail",
            "com.google.android.youtube:id/thumbnail_layout",
            "com.google.android.youtube:id/video_thumbnail"
        )

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
