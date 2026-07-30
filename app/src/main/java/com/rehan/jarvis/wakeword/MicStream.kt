package com.rehan.jarvis.wakeword

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * Continuous 16kHz mono mic stream. Har baar 1280 samples deta hai (80ms).
 */
class MicStream {

    private var record: AudioRecord? = null
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false

        return try {
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, WakeWordDetector.CHUNK_SAMPLES * 4)
            )
            if (record?.state != AudioRecord.STATE_INITIALIZED) {
                record?.release(); record = null
                return false
            }
            record?.startRecording()
            running = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "mic start failed", e)
            false
        }
    }

    /** Blocking read. Null matlab stream band ho gaya. */
    fun readChunk(): FloatArray? {
        val rec = record ?: return null
        if (!running) return null

        val shorts = ShortArray(WakeWordDetector.CHUNK_SAMPLES)
        var offset = 0
        while (offset < shorts.size) {
            val read = rec.read(shorts, offset, shorts.size - offset)
            if (read <= 0) return null
            offset += read
        }
        return FloatArray(shorts.size) { i -> shorts[i].toFloat() }
    }

    fun stop() {
        running = false
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        record?.release()
        record = null
    }

    companion object {
        private const val TAG = "MicStream"
        const val SAMPLE_RATE = 16000
    }
}
