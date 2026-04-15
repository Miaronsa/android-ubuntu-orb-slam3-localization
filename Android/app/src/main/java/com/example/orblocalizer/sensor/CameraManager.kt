package com.example.orblocalizer.sensor

import android.content.Context
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.orblocalizer.util.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"
        const val DEFAULT_FPS = 30
        const val DEFAULT_WIDTH = 640
        const val DEFAULT_HEIGHT = 480
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var frameCallback: ((ByteArray, Long) -> Unit)? = null
    private var isRunning = false

    fun setFrameCallback(callback: (frameData: ByteArray, timestampUs: Long) -> Unit) {
        frameCallback = callback
    }

    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        fps: Int = DEFAULT_FPS,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProvider = suspendCancellableCoroutine { continuation ->
            cameraProviderFuture.addListener({
                try {
                    continuation.resume(cameraProviderFuture.get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(width, height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            processFrame(imageProxy)
        }

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
            isRunning = true
            Logger.i(TAG, "Camera started: ${width}x${height} @ ${fps}fps")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start camera", e)
            throw e
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val timestampUs = imageProxy.imageInfo.timestamp / 1000L
            val yuvData = imageProxyToYUV(imageProxy)
            frameCallback?.invoke(yuvData, timestampUs)
        } catch (e: Exception) {
            Logger.e(TAG, "Error processing frame", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToYUV(imageProxy: ImageProxy): ByteArray {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        return nv21
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        isRunning = false
        Logger.i(TAG, "Camera stopped")
    }

    fun isRunning(): Boolean = isRunning

    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
