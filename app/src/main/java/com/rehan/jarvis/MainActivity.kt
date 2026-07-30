package com.rehan.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.rehan.jarvis.core.AssistantEngine
import com.rehan.jarvis.core.Intents
import com.rehan.jarvis.service.JarvisForegroundService
import com.rehan.jarvis.ui.AssistantScreen
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var engine: AssistantEngine

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result UI me reflect ho jaata hai */ }

    /** Camera se photo lo aur Gemini se poochho "ye kya hai?" */
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap == null) return@registerForActivityResult
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        engine.sendImage(base64, "Is photo me kya hai? Do line me batao.")
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = AssistantEngine.get(this)
        askPermissions()

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AssistantScreen(
                        engine = engine,
                        onStartService = {
                            setAutostart(true)
                            JarvisForegroundService.start(this)
                            askBatteryExemption()
                        },
                        onStopService = {
                            setAutostart(false)
                            JarvisForegroundService.stop(this)
                        },
                        hasMicPermission = { hasPermission(Manifest.permission.RECORD_AUDIO) },
                        onRequestPermissions = { askPermissions() },
                        onOpenCamera = { openCamera() }
                    )
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Widget ya Quick Settings tile se aaye to seedha sunna shuru karo. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(Intents.EXTRA_START_LISTENING, false)) {
            intent.removeExtra(Intents.EXTRA_START_LISTENING)
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                engine.startListening()
            } else {
                askPermissions()
            }
        }

        if (intent.getBooleanExtra(Intents.EXTRA_OPEN_CAMERA, false)) {
            intent.removeExtra(Intents.EXTRA_OPEN_CAMERA)
            openCamera()
        }
    }

    private fun openCamera() {
        if (hasPermission(Manifest.permission.CAMERA)) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun askPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filterNot { hasPermission(it) }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    /** Background me service zinda rehne ke liye battery exemption maango. */
    private fun askBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun setAutostart(enabled: Boolean) {
        getSharedPreferences("jarvis", MODE_PRIVATE).edit()
            .putBoolean("autostart", enabled).apply()
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
