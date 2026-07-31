package com.rehan.jarvis.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.rehan.jarvis.core.AssistantEngine
import java.io.ByteArrayOutputStream

/**
 * Bina kisi button ke khud photo leta hai.
 *
 * Pehle hum system camera app kholte the, jahan user ko khud shutter dabana
 * padta tha. Ab CameraX se Jarvis khud click karta hai aur photo seedha
 * Gemini ko bhej deta hai. Screen pe kuch dikhta bhi nahi — bas ek jhalak.
 */
class CameraCaptureActivity : ComponentActivity() {

    private var question = DEFAULT_QUESTION
    private var useFront = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            capture()
        } else {
            toast("Camera permission ke bina photo nahi le sakta.")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        question = intent.getStringExtra(EXTRA_QUESTION)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_QUESTION
        useFront = intent.getBooleanExtra(EXTRA_FRONT, false)

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) capture() else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun capture() {
        val future = ProcessCameraProvider.getInstance(this)
        val mainExecutor = ContextCompat.getMainExecutor(this)

        future.addListener({
            try {
                val provider = future.get()

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val selector = if (useFront) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                provider.unbindAll()
                provider.bindToLifecycle(this, selector, imageCapture)

                // Camera ko thodi der do — warna dhundhli ya kaali photo aati hai
                Handler(Looper.getMainLooper()).postDelayed({
                    shoot(imageCapture, provider, mainExecutor)
                }, SETTLE_MS)
            } catch (e: Exception) {
                Log.e(TAG, "camera start nahi hua", e)
                toast("Camera khul nahi paya.")
                finish()
            }
        }, mainExecutor)
    }

    private fun shoot(
        imageCapture: ImageCapture,
        provider: ProcessCameraProvider,
        executor: java.util.concurrent.Executor
    ) {
        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {

            override fun onCaptureSuccess(image: ImageProxy) {
                val base64 = toBase64(image)
                image.close()
                provider.unbindAll()

                if (base64 != null) {
                    AssistantEngine.get(applicationContext).sendImage(base64, question)
                } else {
                    toast("Photo process nahi ho payi.")
                }
                finish()
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "photo fail", exception)
                provider.unbindAll()
                toast("Photo nahi le paya.")
                finish()
            }
        })
    }

    /** JPEG bytes -> seedha karo -> chhota karo -> base64. */
    private fun toBase64(image: ImageProxy): String? {
        return try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            val rotation = image.imageInfo.rotationDegrees
            val straight = if (rotation == 0) {
                decoded
            } else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }

            val small = scaleDown(straight)
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 80, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "bitmap fail", e)
            null
        }
    }

    /** Badi photo upload me time khaati hai, isliye chhoti kar dete hain. */
    private fun scaleDown(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_SIDE) return source

        val ratio = MAX_SIDE.toFloat() / longest
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "CameraCapture"

        const val EXTRA_QUESTION = "com.rehan.jarvis.CAPTURE_QUESTION"
        const val EXTRA_FRONT = "com.rehan.jarvis.CAPTURE_FRONT"

        private const val DEFAULT_QUESTION = "Is photo me kya hai? Do line me batao."
        private const val SETTLE_MS = 700L
        private const val MAX_SIDE = 1024
    }
}
