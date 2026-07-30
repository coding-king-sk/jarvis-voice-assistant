package com.rehan.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.rehan.jarvis.core.AssistantEngine
import com.rehan.jarvis.service.JarvisForegroundService
import com.rehan.jarvis.ui.AssistantScreen

class MainActivity : ComponentActivity() {

    private lateinit var engine: AssistantEngine

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result UI me reflect ho jaata hai */ }

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
                        onRequestPermissions = { askPermissions() }
                    )
                }
            }
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
