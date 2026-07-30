package com.rehan.jarvis.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rehan.jarvis.MainActivity
import com.rehan.jarvis.core.Intents

/**
 * Quick Settings tile — notification shade se seedha Jarvis.
 * Pehli baar user ko tile ko manually add karna padta hai:
 * shade neeche kheencho > pencil/edit > "Jarvis" ko drag karo.
 */
class JarvisTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.label = "Jarvis"
            it.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intents.EXTRA_START_LISTENING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(Intents.EXTRA_START_LISTENING, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
