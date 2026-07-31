package com.rehan.jarvis.tools

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.rehan.jarvis.MainActivity
import com.rehan.jarvis.camera.CameraCaptureActivity
import com.rehan.jarvis.core.Intents
import com.rehan.jarvis.memory.MemoryStore
import com.rehan.jarvis.service.JarvisAccessibilityService
import com.rehan.jarvis.service.JarvisNotificationListener
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Claude jo tool call karta hai, uska asli kaam yahan hota hai.
 * Har function ek chhota sa string return karta hai jo wapas Claude ko jaata hai.
 */
class ToolExecutor(private val context: Context) {

    fun execute(name: String, args: JSONObject): String = try {
        when (name) {
            // Communication
            "make_call" -> makeCall(args.optString("contact_name"))
            "send_whatsapp" -> sendWhatsApp(args.optString("contact_name"), args.optString("message"))
            "send_sms" -> sendSms(args.optString("contact_name"), args.optString("message"))

            // Apps
            "open_app" -> openApp(args.optString("app_name"))

            // Time
            "set_alarm" -> setAlarm(
                args.optInt("hour", -1),
                args.optInt("minute", 0),
                args.optString("label", "Jarvis alarm")
            )
            "set_reminder" -> setReminder(
                args.optString("text"),
                args.optInt("minutes_from_now", 0)
            )

            // Device
            "set_volume" -> setVolume(args.optInt("level_percent", -1))
            "set_brightness" -> setBrightness(args.optInt("level_percent", -1))
            "toggle_wifi" -> toggleWifi(args.optBoolean("enable", true))
            "toggle_torch" -> toggleTorch(args.optBoolean("enable", true))
            "set_ringer_mode" -> setRingerMode(args.optString("mode", "normal"))
            "set_dnd" -> setDnd(args.optBoolean("enable", true))
            "open_settings_page" -> openSettingsPage(args.optString("page"))
            "get_device_status" -> deviceStatus()

            // Screen ke kaam
            "take_screenshot" -> takeScreenshot()
            "press_key" -> pressKey(args.optString("key"))

            // Camera
            "take_photo" -> takePhoto(args.optString("camera"), args.optString("question"))

            // Padhne wale kaam
            "read_clipboard" -> readClipboard()
            "read_screen" -> readScreen()
            "read_notifications" -> readNotifications(args.optInt("count", 5))
            "reply_last_message" -> replyLastMessage(args.optString("message"))

            // Music
            "media_control" -> mediaControl(args.optString("action"))
            "play_music" -> playMusic(args.optString("query"))
            "play_on_youtube" -> playOnYoutube(args.optString("query"))

            // Yaaddaasht — phone me hi save hoti hai, kahin bahar nahi jaati
            "remember_fact" -> MemoryStore.add(context, args.optString("fact"))
            "recall_facts" -> MemoryStore.recall(context)
            "forget_memory" -> MemoryStore.forget(context, args.optString("about"))

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
        val text = message.ifBlank { "Hi" }

        val url = "https://wa.me/$number?text=" + Uri.encode(text)
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
        sms.sendTextMessage(contact.number, null, message.ifBlank { "Hi" }, null, null)
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

    // ---------- Screen ke kaam ----------

    private fun takeScreenshot(): String {
        return when (JarvisAccessibilityService.takeScreenshot()) {
            null -> {
                JarvisAccessibilityService.openSettings(context)
                "Screenshot ke liye Accessibility me Jarvis on karna padega, settings khol di hai."
            }
            true -> "Screenshot le liya, gallery me save ho gaya."
            false -> "Is Android version pe screenshot nahi le sakta. Power aur volume down saath dabao."
        }
    }

    private fun pressKey(key: String): String {
        val label = when (key.lowercase().trim()) {
            "home" -> "Home"
            "back", "peeche" -> "Back"
            "recents", "recent" -> "Recent apps"
            "notifications" -> "Notification panel"
            "quick_settings" -> "Quick settings"
            "lock" -> "Screen lock"
            else -> return "Ye button samajh nahi aaya."
        }
        return when (JarvisAccessibilityService.pressKey(key)) {
            null -> {
                JarvisAccessibilityService.openSettings(context)
                "Iske liye Accessibility me Jarvis on karna padega, settings khol di hai."
            }
            true -> "$label kar diya."
            false -> "$label is phone pe nahi ho paya."
        }
    }

    // ---------- Camera ----------

    /**
     * Jarvis khud photo kheenchta hai — shutter dabane ki zarurat nahi.
     * Camera permission na ho to app khol ke permission maang lete hain.
     */
    private fun takePhoto(camera: String, question: String): String {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            val ask = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Intents.EXTRA_OPEN_CAMERA, true)
            return try {
                context.startActivity(ask)
                "Camera ki permission chahiye, app khol di hai. Allow kar do phir bolna."
            } catch (e: Exception) {
                "Camera permission nahi hai."
            }
        }

        val word = camera.lowercase()
        val front = word.contains("front") || word.contains("selfie") ||
            word.contains("saamne") || word.contains("meri")

        val intent = Intent(context, CameraCaptureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(CameraCaptureActivity.EXTRA_FRONT, front)
        if (question.isNotBlank()) {
            intent.putExtra(CameraCaptureActivity.EXTRA_QUESTION, question)
        }

        return try {
            context.startActivity(intent)
            "Photo le raha hoon, ek second."
        } catch (e: Exception) {
            Log.e(TAG, "camera activity fail", e)
            "Camera khul nahi paya."
        }
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

    /** Torch ko app se seedha control kiya ja sakta hai — koi permission nahi chahiye. */
    private fun toggleTorch(enable: Boolean): String {
        val cm = context.getSystemService(CameraManager::class.java)
            ?: return "Camera service nahi mili."
        val flashCamera = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Is phone me flash light nahi hai."

        cm.setTorchMode(flashCamera, enable)
        return if (enable) "Torch on kar di." else "Torch off kar di."
    }

    /** silent / vibrate / normal */
    private fun setRingerMode(mode: String): String {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            openDndAccess()
            return "Ringer badalne ke liye ek permission chahiye, maine settings khol di hai."
        }

        val am = context.getSystemService(AudioManager::class.java)
        return when (mode.lowercase().trim()) {
            "silent", "mute", "chup" -> {
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
                "Phone silent kar diya."
            }
            "vibrate", "vibration" -> {
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                "Phone vibrate pe laga diya."
            }
            else -> {
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                "Phone normal mode pe aa gaya."
            }
        }
    }

    private fun setDnd(enable: Boolean): String {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            openDndAccess()
            return "Do Not Disturb ke liye permission chahiye, maine settings khol di hai."
        }
        nm.setInterruptionFilter(
            if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return if (enable) "Do Not Disturb on kar diya." else "Do Not Disturb off kar diya."
    }

    private fun openDndAccess() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Bluetooth, hotspot, location wagairah ka settings page kholo. */
    private fun openSettingsPage(page: String): String {
        val key = page.lowercase().trim()
        val (action, label) = when {
            key.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth"
            key.contains("hotspot") || key.contains("tether") ->
                "android.settings.TETHER_SETTINGS" to "Hotspot"
            key.contains("location") || key.contains("gps") ->
                Settings.ACTION_LOCATION_SOURCE_SETTINGS to "Location"
            key.contains("battery") -> Intent.ACTION_POWER_USAGE_SUMMARY to "Battery"
            key.contains("data") || key.contains("mobile") ->
                Settings.ACTION_DATA_ROAMING_SETTINGS to "Mobile data"
            key.contains("display") || key.contains("screen") ->
                Settings.ACTION_DISPLAY_SETTINGS to "Display"
            key.contains("sound") -> Settings.ACTION_SOUND_SETTINGS to "Sound"
            else -> Settings.ACTION_SETTINGS to "Settings"
        }
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "$label settings khol di."
        } catch (e: Exception) {
            "$label ka page is phone pe nahi mila."
        }
    }

    private fun deviceStatus(): String {
        val bm = context.getSystemService(BatteryManager::class.java)
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = bm?.isCharging == true

        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volPercent = if (max > 0) cur * 100 / max else 0

        val ringer = when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "normal"
        }

        val time = SimpleDateFormat("hh:mm a, EEE d MMM", Locale.US)
            .format(System.currentTimeMillis())

        val chargeText = if (charging) " aur charge ho raha hai" else ""
        return "Battery: $battery percent$chargeText. Volume: $volPercent percent. " +
            "Ringer: $ringer. Time: $time."
    }

    // ---------- Padhne wale kaam ----------

    private fun readClipboard(): String {
        val cm = context.getSystemService(ClipboardManager::class.java)
            ?: return "Clipboard nahi mila."
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) return "Clipboard khaali hai."

        val text = clip.getItemAt(0).coerceToText(context).toString().trim()
        if (text.isBlank()) return "Clipboard me kuch text nahi hai."
        return "Clipboard me ye hai: ${text.take(500)}"
    }

    private fun readScreen(): String {
        val text = JarvisAccessibilityService.readScreen()
        return when {
            text == null -> {
                JarvisAccessibilityService.openSettings(context)
                "Screen padhne ke liye Accessibility me Jarvis ko on karna padega, settings khol di hai."
            }
            text.isBlank() -> "Screen pe padhne layak kuch nahi mila."
            else -> "Screen pe ye likha hai: $text"
        }
    }

    private fun readNotifications(count: Int): String {
        if (!JarvisNotificationListener.isEnabled(context)) {
            JarvisNotificationListener.openSettings(context)
            return "Notifications padhne ki permission chahiye, maine settings khol di hai. " +
                "Wahan Jarvis ko on kar do."
        }
        return JarvisNotificationListener.summary(count.coerceIn(1, 10))
    }

    private fun replyLastMessage(message: String): String {
        if (message.isBlank()) return "Reply me kya likhna hai wo samajh nahi aaya."
        if (!JarvisNotificationListener.isEnabled(context)) {
            JarvisNotificationListener.openSettings(context)
            return "Reply bhejne ke liye notification permission chahiye, settings khol di hai."
        }
        return JarvisNotificationListener.replyToLast(context, message)
    }

    // ---------- Music ----------

    private fun mediaControl(action: String): String =
        when (action.lowercase().trim()) {
            "play" -> { MediaTools.play(context); "Music chala diya." }
            "pause", "stop" -> { MediaTools.pause(context); "Music rok diya." }
            "next", "skip" -> { MediaTools.next(context); "Agla gaana laga diya." }
            "previous", "prev", "back" -> { MediaTools.previous(context); "Pichla gaana laga diya." }
            else -> { MediaTools.playPause(context); "Ho gaya." }
        }

    private fun playMusic(query: String): String {
        if (query.isBlank()) {
            MediaTools.play(context)
            return "Music chala diya."
        }
        return playOnYoutube(query)
    }

    /**
     * Gaana sach me BAJNA chahiye, sirf search khulna kaafi nahi.
     *
     * Isliye seedha "play from search" intent bhejte hain — ye YouTube ko bolta
     * hai "ye dhoondho aur pehla result chala do". Jo app kaam kar jaaye, wahin
     * ruk jaate hain:
     *   1. YouTube Music (sabse saaf autoplay)
     *   2. YouTube app
     *   3. Koi bhi music app jo ye intent samajhti ho (Spotify wagairah)
     *   4. YouTube app me search + Accessibility se khud pehla thumbnail tap
     *   5. Browser
     */
    private fun playOnYoutube(query: String): String {
        val term = query.ifBlank { "trending songs" }

        // 1-3) "dhoondho aur chala do" intent
        for (pkg in PLAY_PACKAGES) {
            if (pkg != null && !isInstalled(pkg)) continue
            try {
                val play = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    if (pkg != null) setPackage(pkg)
                    putExtra(SearchManager.QUERY, term)
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(play)

                // Kabhi kabhi YouTube sirf results dikha deta hai — to khud tap kar denge
                if (pkg == YT_PKG && JarvisAccessibilityService.isRunning()) {
                    JarvisAccessibilityService.tapFirstYoutubeResult()
                }
                return "$term chala diya."
            } catch (_: Exception) {
                // agli app try karo
            }
        }

        // 4) YouTube me search kholo aur pehli video khud tap karo
        if (isInstalled(YT_PKG)) {
            try {
                val search = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage(YT_PKG)
                    putExtra("query", term)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(search)
                return if (JarvisAccessibilityService.isRunning()) {
                    JarvisAccessibilityService.tapFirstYoutubeResult()
                    "YouTube pe $term chala raha hoon."
                } else {
                    JarvisAccessibilityService.openSettings(context)
                    "YouTube pe $term search kar diya. Gaana khud chalu ho jaaye iske liye " +
                        "Accessibility me Jarvis on kar do — settings khol di hai."
                }
            } catch (_: Exception) {
            }
        }

        // 5) Browser
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(term))
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(web)
        return "YouTube pe $term dhoondh raha hoon."
    }

    private fun isInstalled(pkg: String): Boolean =
        try {
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        } catch (e: Exception) {
            false
        }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "ToolExecutor"
        private const val YT_PKG = "com.google.android.youtube"
        private const val YT_MUSIC_PKG = "com.google.android.apps.youtube.music"

        /** Kis order me try karna hai. null = jo bhi app ye intent handle kar sake. */
        private val PLAY_PACKAGES = listOf(YT_MUSIC_PKG, YT_PKG, null)
    }
}
