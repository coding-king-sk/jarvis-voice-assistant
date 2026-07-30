package com.rehan.jarvis.tools

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Gemini jo function call karta hai, uska asli kaam yahan hota hai.
 * Har function ek chhota sa string return karta hai jo wapas Gemini ko jaata hai.
 */
class ToolExecutor(private val context: Context) {

    fun execute(name: String, args: JSONObject): String = try {
        when (name) {
            "make_call" -> makeCall(args.optString("contact_name"))
            "send_whatsapp" -> sendWhatsApp(args.optString("contact_name"), args.optString("message"))
            "send_sms" -> sendSms(args.optString("contact_name"), args.optString("message"))
            "open_app" -> openApp(args.optString("app_name"))
            "set_alarm" -> setAlarm(
                args.optInt("hour", -1),
                args.optInt("minute", 0),
                args.optString("label", "Jarvis alarm")
            )
            "set_reminder" -> setReminder(
                args.optString("text"),
                args.optInt("minutes_from_now", 0)
            )
            "set_volume" -> setVolume(args.optInt("level_percent", -1))
            "set_brightness" -> setBrightness(args.optInt("level_percent", -1))
            "toggle_wifi" -> toggleWifi(args.optBoolean("enable", true))
            "get_device_status" -> deviceStatus()
            else -> "Ye kaam abhi support nahi karta."
        }
    } catch (e: Exception) {
        Log.e(TAG, "Tool $name failed", e)
        "Kaam karte waqt error aa gaya: ${e.message}"
    }

    // ---------- Communication ----------

    private fun makeCall(contactName: String): String {
        if (!hasPermission(Manifest.permission.CALL_PHONE)) return "Call karne ki permission nahi hai."
        val contact = ContactResolver.resolve(context, contactName)
            ?: return "'$contactName' naam ka koi contact nahi mila."

        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.number}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "${contact.name} ko call lag rahi hai."
    }

    private fun sendWhatsApp(contactName: String, message: String): String {
        val contact = ContactResolver.resolve(context, contactName)
            ?: return "'$contactName' naam ka koi contact nahi mila."
        val number = ContactResolver.toWhatsAppNumber(contact.number)

        val url = "https://wa.me/$number?text=" + Uri.encode(message)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "${contact.name} ke liye WhatsApp khol diya, bas send dabana hai."
        } catch (e: Exception) {
            "WhatsApp install nahi hai."
        }
    }

    private fun sendSms(contactName: String, message: String): String {
        if (!hasPermission(Manifest.permission.SEND_SMS)) return "SMS bhejne ki permission nahi hai."
        val contact = ContactResolver.resolve(context, contactName)
            ?: return "'$contactName' naam ka koi contact nahi mila."

        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        sms.sendTextMessage(contact.number, null, message, null, null)
        return "${contact.name} ko SMS bhej diya."
    }

    // ---------- Apps ----------

    private fun openApp(appName: String): String {
        val pm = context.packageManager
        val target = appName.lowercase().trim()

        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)

        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase() == target
        } ?: apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(target)
        } ?: apps.firstOrNull {
            target.contains(it.loadLabel(pm).toString().lowercase())
        }

        if (match == null) return "'$appName' naam ki koi app nahi mili."

        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "App khul nahi payi."
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return "${match.loadLabel(pm)} khol diya."
    }

    // ---------- Time ----------

    private fun setAlarm(hour: Int, minute: Int, label: String): String {
        if (hour !in 0..23 || minute !in 0..59) return "Alarm ka time samajh nahi aaya."
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val t = String.format(Locale.US, "%02d:%02d", hour, minute)
            "$t ka alarm laga diya."
        } catch (e: Exception) {
            "Clock app nahi mili."
        }
    }

    private fun setReminder(text: String, minutesFromNow: Int): String {
        if (minutesFromNow <= 0) return "Reminder ka time samajh nahi aaya."
        val triggerAt = System.currentTimeMillis() + minutesFromNow * 60_000L

        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TEXT, text)
        val pending = PendingIntent.getBroadcast(
            context,
            triggerAt.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(AlarmManager::class.java)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }

        val time = SimpleDateFormat("hh:mm a", Locale.US).format(triggerAt)
        return "Theek hai, $time pe yaad dila dunga."
    }

    // ---------- Device ----------

    private fun setVolume(percent: Int): String {
        if (percent !in 0..100) return "Volume level samajh nahi aaya."
        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * percent / 100.0).toInt().coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return "Volume $percent percent kar diya."
    }

    private fun setBrightness(percent: Int): String {
        if (percent !in 0..100) return "Brightness level samajh nahi aaya."
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return "Brightness badalne ki permission chahiye, settings khol di hai."
        }
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            (255 * percent / 100).coerceIn(1, 255)
        )
        return "Brightness $percent percent kar di."
    }

    /**
     * Android 10+ pe app WiFi ko silently on/off NAHI kar sakti.
     * Isliye hum official settings panel kholte hain.
     */
    private fun toggleWifi(enable: Boolean): String {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_WIFI
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val word = if (enable) "on" else "off"
        return "Android WiFi ko app se seedha $word karne nahi deta, maine panel khol diya hai."
    }

    private fun deviceStatus(): String {
        val bm = context.getSystemService(BatteryManager::class.java)
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volPercent = if (max > 0) cur * 100 / max else 0

        val time = SimpleDateFormat("hh:mm a, EEE d MMM", Locale.US).format(System.currentTimeMillis())

        return "Battery: $battery percent. Volume: $volPercent percent. Time: $time."
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object { private const val TAG = "ToolExecutor" }
}
