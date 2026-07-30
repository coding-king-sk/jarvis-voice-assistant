package com.rehan.jarvis.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Phone restart hone par service wapas chalu kar do. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            if (prefs.getBoolean("autostart", false)) {
                JarvisForegroundService.start(context)
            }
        }
    }
}
