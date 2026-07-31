package com.rehan.jarvis.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * "Beech me tokna" wala feature.
 *
 * Jab Jarvis bol raha hota hai, ye mic sunta rehta hai. User bolna shuru kare to
 * turant batata hai, taaki Jarvis chup ho jaaye aur sun-ne lag jaaye — bilkul
 * asli phone call jaisa.
 *
 * Speaker ki apni awaaz mic me na aaye, iske liye phone ka hardware echo
 * canceller (AEC) aur noise suppressor on karte hain.
 */
class BargeInDetector(private val context: Context) {

    @Volatile
    private var running = false

    private var thread: Thread? = null

    fun start(onBargeIn: () -> Unit) {
        if (running) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return

        running = true
        thread = Thread { listenLoop(onBargeIn) }.apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread = null
    }

    private fun listenLoop(onBargeIn: () -> Unit) {
        var record: AudioRecord? = null
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null

        try {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "mic nahi mila, barge-in off")
                return
            }

            // Speaker ki awaaz mic me wapas na aaye
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
            }

            record.startRecording()

            val buffer = ShortArray(minBuffer / 2)
            val startedAt = System.currentTimeMillis()
            var loudFrames = 0

            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                // Shuru ke kuch milliseconds ignore karo — speaker abhi warm up ho raha hai
                if (System.currentTimeMillis() - startedAt < WARMUP_MS) continue

                var sum = 0.0
                for (i in 0 until read) {
                    val v = buffer[i].toDouble()
                    sum += v * v
                }
                val rms = sqrt(sum / read)

                if (rms > THRESHOLD) {
                    loudFrames++
                    if (loudFrames >= NEEDED_FRAMES) {
                        running = false
                        Handler(Looper.getMainLooper()).post(onBargeIn)
                        break
                    }
                } else {
                    loudFrames = 0
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "mic permission nahi hai")
        } catch (e: Exception) {
            Log.w(TAG, "barge-in fail: ${e.message}")
        } finally {
            try {
                aec?.release()
                ns?.release()
                record?.let {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
                    it.release()
                }
            } catch (_: Exception) {
            }
            running = false
        }
    }

    companion object {
        private const val TAG = "BargeInDetector"
        private const val SAMPLE_RATE = 16000

        /** Itni awaaz aaye tabhi maano ki user bol raha hai. */
        private const val THRESHOLD = 2600.0

        /** Itne frames lagataar tez rahe tabhi tokna maano — khaansi ya thak se na kate. */
        private const val NEEDED_FRAMES = 3

        private const val WARMUP_MS = 450L
    }
}
