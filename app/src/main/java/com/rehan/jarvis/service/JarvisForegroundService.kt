package com.rehan.jarvis.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rehan.jarvis.JarvisApp
import com.rehan.jarvis.MainActivity
import com.rehan.jarvis.R
import com.rehan.jarvis.core.AssistantEngine
import com.rehan.jarvis.core.AssistantState
import com.rehan.jarvis.wakeword.MicStream
import com.rehan.jarvis.wakeword.WakeWordDetector
import kotlin.concurrent.thread

/**
 * Hamesha background me chalti hai, wake word ke liye sunti hai.
 * Wake word milte hi mic chhod deti hai taaki SpeechRecognizer use kar sake.
 */
class JarvisForegroundService : Service() {

    private lateinit var engine: AssistantEngine
    private val detector by lazy { WakeWordDetector(this) }
    private val mic = MicStream()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var listeningForWakeWord = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = AssistantEngine.get(this)
        engine.onTurnFinished = {
            // Turn khatam — thoda ruk kar wake word listening wapas on karo
            main.postDelayed({ startWakeWordLoop() }, 600)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (!detector.isReady) {
            val loaded = detector.load()
            if (!loaded) {
                Log.w(TAG, "Wake word models nahi mile — sirf manual mode chalega")
                updateNotification("Wake word model missing — app se mic button use karo")
                return START_STICKY
            }
        }

        startWakeWordLoop()
        return START_STICKY
    }

    private fun startWakeWordLoop() {
        if (listeningForWakeWord) return
        if (!detector.isReady) return
        if (!mic.start()) {
            Log.e(TAG, "Mic start nahi hua")
            return
        }

        listeningForWakeWord = true
        detector.reset()
        updateNotification(getString(R.string.service_notification_text))

        worker = thread(name = "wake-word", isDaemon = true) {
            while (listeningForWakeWord) {
                val chunk = mic.readChunk() ?: break
                if (detector.accept(chunk)) {
                    Log.i(TAG, "Wake word detected!")
                    onWakeWordDetected()
                    break
                }
            }
        }
    }

    private fun stopWakeWordLoop() {
        listeningForWakeWord = false
        mic.stop()
        worker = null
    }

    private fun onWakeWordDetected() {
        // Mic free karo — SpeechRecognizer aur AudioRecord saath me nahi chal sakte
        stopWakeWordLoop()
        main.post {
            updateNotification(getString(R.string.listening))
            if (engine.state.value == AssistantState.IDLE) {
                engine.startListening()
            } else {
                startWakeWordLoop()
            }
        }
    }

    private fun buildNotification(text: String = getString(R.string.service_notification_text)): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, JarvisForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, JarvisApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.stop), stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        stopWakeWordLoop()
        detector.close()
        engine.onTurnFinished = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "JarvisService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.rehan.jarvis.STOP"

        fun start(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, JarvisForegroundService::class.java))
        }
    }
}
