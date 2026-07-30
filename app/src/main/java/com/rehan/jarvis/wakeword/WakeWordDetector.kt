package com.rehan.jarvis.wakeword

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer

/**
 * openWakeWord pipeline, teen ONNX models:
 *
 *   audio (1280 samples = 80ms)
 *     -> melspectrogram.onnx    -> mel frames [32 bins]
 *     -> embedding_model.onnx   -> 96-dim embedding (76 mel frames chahiye)
 *     -> wakeword.onnx          -> score 0..1 (16 embeddings chahiye)
 *
 * Teeno files app/src/main/assets/ me honi chahiye.
 * Custom wake word train karne ke liye docs/WAKE_WORD.md padho.
 */
class WakeWordDetector(private val context: Context) {

    private var env: OrtEnvironment? = null
    private var melSession: OrtSession? = null
    private var embSession: OrtSession? = null
    private var wakeSession: OrtSession? = null

    private val melBuffer = ArrayDeque<FloatArray>()   // har frame me 32 values
    private val embBuffer = ArrayDeque<FloatArray>()   // har embedding 96 values

    var threshold: Float = 0.5f
    var isReady: Boolean = false
        private set

    /** Models load karo. false matlab assets missing hain. */
    fun load(): Boolean {
        return try {
            env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(1) }
            melSession = env!!.createSession(readAsset(MEL_MODEL), opts)
            embSession = env!!.createSession(readAsset(EMB_MODEL), opts)
            wakeSession = env!!.createSession(readAsset(WAKE_MODEL), opts)
            isReady = true
            Log.i(TAG, "Wake word models loaded")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Wake word models load nahi hue: ${e.message}")
            isReady = false
            false
        }
    }

    /**
     * 1280 audio samples (16kHz mono, -1..1 float) do.
     * Wake word mila to true.
     */
    fun accept(samples: FloatArray): Boolean {
        if (!isReady) return false
        return try {
            computeMel(samples)
            computeEmbeddings()
            score() >= threshold
        } catch (e: Exception) {
            Log.e(TAG, "inference failed", e)
            false
        }
    }

    /** Wake ke baad buffers saaf karo, warna turant dobara trigger hoga. */
    fun reset() {
        melBuffer.clear()
        embBuffer.clear()
    }

    private fun computeMel(samples: FloatArray) {
        val ortEnv = env ?: return
        val session = melSession ?: return

        val input = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(samples),
            longArrayOf(1, samples.size.toLong())
        )
        input.use {
            session.run(mapOf(session.inputNames.first() to it)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = result[0].value as Array<Array<Array<FloatArray>>> // [1][1][frames][32]
                for (frame in out[0][0]) {
                    // openWakeWord ka standard normalization
                    val normalized = FloatArray(frame.size) { i -> frame[i] / 10f + 2f }
                    melBuffer.addLast(normalized)
                }
                while (melBuffer.size > MEL_KEEP) melBuffer.removeFirst()
            }
        }
    }

    private fun computeEmbeddings() {
        val ortEnv = env ?: return
        val session = embSession ?: return
        if (melBuffer.size < EMB_WINDOW) return

        val frames = melBuffer.toList().takeLast(EMB_WINDOW)
        val flat = FloatArray(EMB_WINDOW * MEL_BINS)
        var idx = 0
        for (frame in frames) for (v in frame) flat[idx++] = v

        val input = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(flat),
            longArrayOf(1, EMB_WINDOW.toLong(), MEL_BINS.toLong(), 1)
        )
        input.use {
            session.run(mapOf(session.inputNames.first() to it)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = result[0].value as Array<Array<Array<FloatArray>>> // [1][1][1][96]
                embBuffer.addLast(out[0][0][0].copyOf())
                while (embBuffer.size > WAKE_WINDOW) embBuffer.removeFirst()
            }
        }
    }

    private fun score(): Float {
        val ortEnv = env ?: return 0f
        val session = wakeSession ?: return 0f
        if (embBuffer.size < WAKE_WINDOW) return 0f

        val flat = FloatArray(WAKE_WINDOW * EMB_SIZE)
        var idx = 0
        for (emb in embBuffer) for (v in emb) flat[idx++] = v

        val input = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(flat),
            longArrayOf(1, WAKE_WINDOW.toLong(), EMB_SIZE.toLong())
        )
        input.use {
            session.run(mapOf(session.inputNames.first() to it)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = result[0].value as Array<FloatArray> // [1][1]
                return out[0][0]
            }
        }
    }

    private fun readAsset(name: String): ByteArray =
        context.assets.open(name).use { it.readBytes() }

    fun close() {
        try {
            melSession?.close(); embSession?.close(); wakeSession?.close()
        } catch (_: Exception) {
        }
        melSession = null; embSession = null; wakeSession = null
        isReady = false
        reset()
    }

    companion object {
        private const val TAG = "WakeWord"
        const val MEL_MODEL = "melspectrogram.onnx"
        const val EMB_MODEL = "embedding_model.onnx"
        const val WAKE_MODEL = "wakeword.onnx"

        const val CHUNK_SAMPLES = 1280   // 80ms @ 16kHz
        private const val MEL_BINS = 32
        private const val EMB_WINDOW = 76
        private const val EMB_SIZE = 96
        private const val WAKE_WINDOW = 16
        private const val MEL_KEEP = 10 * EMB_WINDOW
    }
}
