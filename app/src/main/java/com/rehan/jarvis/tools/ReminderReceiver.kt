package com.rehan.jarvis.tools

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rehan.jarvis.JarvisApp
import com.rehan.jarvis.R

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: "Reminder"

        val notification = NotificationCompat.Builder(context, JarvisApp.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Jarvis Reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            ?.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val EXTRA_TEXT = "reminder_text"
    }
}
