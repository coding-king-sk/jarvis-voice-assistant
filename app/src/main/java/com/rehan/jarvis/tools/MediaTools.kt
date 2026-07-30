package com.rehan.jarvis.tools

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/**
 * Music control ke liye media key events bhejta hai.
 * Ye Spotify, YouTube Music, Gaana, JioSaavn — sab pe kaam karta hai,
 * kyunki hum wahi button dabate hain jo headphone ka button dabata hai.
 * Koi special permission nahi chahiye.
 */
object MediaTools {

    fun dispatch(context: Context, keyCode: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val now = System.currentTimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    fun playPause(context: Context) = dispatch(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun play(context: Context) = dispatch(context, KeyEvent.KEYCODE_MEDIA_PLAY)
    fun pause(context: Context) = dispatch(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun next(context: Context) = dispatch(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous(context: Context) = dispatch(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun stop(context: Context) = dispatch(context, KeyEvent.KEYCODE_MEDIA_STOP)
}
