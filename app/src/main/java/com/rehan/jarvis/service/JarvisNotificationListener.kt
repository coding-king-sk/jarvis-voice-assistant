package com.rehan.jarvis.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.RemoteInput

/** Ek notification ki simple copy. */
data class NotificationItem(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
    val postTime: Long
)

/**
 * Notifications padhne aur unka reply bhejne ke liye.
 *
 * User ko ise manually enable karna padta hai:
 * Settings > Notifications > Device & app notifications > Jarvis
 */
class JarvisNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = true
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val item = NotificationItem(sbn.key, appLabel(sbn.packageName), title, text, sbn.postTime)

        synchronized(recent) {
            recent.removeAll { it.key == sbn.key }
            recent.add(0, item)
            while (recent.size > 30) recent.removeAt(recent.size - 1)
        }

        findReplyAction(sbn.notification)?.let { action ->
            lastReplyItem = item
            lastReplyAction = action
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(recent) { recent.removeAll { it.key == sbn.key } }
    }

    private fun appLabel(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    companion object {
        private const val TAG = "JarvisNotifs"

        @Volatile
        var connected = false
            private set

        private val recent = mutableListOf<NotificationItem>()
        private var lastReplyItem: NotificationItem? = null
        private var lastReplyAction: Notification.Action? = null

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            return flat.contains(context.packageName)
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        /** Bolne layak summary — TTS ke liye chhoti aur saaf. */
        fun summary(limit: Int = 5): String {
            val items = synchronized(recent) { recent.take(limit) }
            if (items.isEmpty()) return "Abhi koi naya notification nahi hai."

            val lines = items.mapIndexed { i, n ->
                val who = if (n.title.isNotBlank()) n.title else n.app
                val what = n.text.take(120)
                "${i + 1}. ${n.app} pe $who: $what"
            }
            return "Aapke paas ${items.size} notification hain. " + lines.joinToString(". ")
        }

        /** Notification me chhupa hua reply button dhoondo. */
        private fun findReplyAction(notification: Notification): Notification.Action? {
            val actions = notification.actions ?: return null
            return actions.firstOrNull { action ->
                action.remoteInputs?.any { it.allowFreeFormInput } == true
            }
        }

        /** Aakhri message ka reply bhejo — app khole bina. */
        fun replyToLast(context: Context, message: String): String {
            val action = lastReplyAction
                ?: return "Abhi kisi message ka reply nahi kar sakta, koi naya message nahi aaya."
            val item = lastReplyItem
            val inputs = action.remoteInputs?.filter { it.allowFreeFormInput }
            if (inputs.isNullOrEmpty()) return "Is notification me reply karne ki jagah nahi hai."

            return try {
                val intent = Intent()
                val bundle = Bundle()
                val compatInputs = inputs.map {
                    RemoteInput.Builder(it.resultKey)
                        .setLabel(it.label)
                        .setAllowFreeFormInput(true)
                        .build()
                }.toTypedArray()
                compatInputs.forEach { bundle.putCharSequence(it.resultKey, message) }
                RemoteInput.addResultsToIntent(compatInputs, intent, bundle)

                action.actionIntent.send(context, 0, intent)
                lastReplyAction = null
                val who = item?.title?.takeIf { it.isNotBlank() } ?: "unhe"
                "$who ko reply bhej diya."
            } catch (e: Exception) {
                Log.e(TAG, "reply failed", e)
                "Reply bhejte waqt dikkat aa gayi."
            }
        }
    }
}
