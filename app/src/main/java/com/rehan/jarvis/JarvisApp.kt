package com.rehan.jarvis

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class JarvisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Jarvis background listening" }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDER,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Jarvis reminders" }
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "jarvis_service"
        const val CHANNEL_REMINDER = "jarvis_reminder"
    }
}
