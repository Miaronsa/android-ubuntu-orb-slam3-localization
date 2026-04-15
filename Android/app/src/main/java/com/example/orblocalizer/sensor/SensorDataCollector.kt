package com.example.orblocalizer.sensor

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.example.orblocalizer.network.DataFrameProtocol
import com.example.orblocalizer.network.NetworkManager
import com.example.orblocalizer.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SensorDataCollector(
    private val context: Context,
    private val networkManager: NetworkManager
) {
    companion object {
        private const val TAG = "SensorDataCollector"
    }

    private val cameraManager = CameraManager(context)
    private val imuSensor = IMUSensor(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(
        lifecycleOwner: LifecycleOwner,
        cameraFps: Int = CameraManager.DEFAULT_FPS,
        imuRateHz: Int = IMUSensor.DEFAULT_SAMPLE_RATE_HZ,
        videoEnabled: Boolean = true,
        imuEnabled: Boolean = true
    ) {
        if (videoEnabled) {
            cameraManager.setFrameCallback { frameData, timestampUs ->
                scope.launch {
                    val frame = DataFrameProtocol.createCameraFrame(frameData, timestampUs)
                    networkManager.sendData(frame)
                }
            }

            scope.launch(Dispatchers.Main) {
                try {
                    cameraManager.startCamera(lifecycleOwner, cameraFps)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to start camera", e)
                }
            }
        }

        if (imuEnabled) {
            imuSensor.setIMUCallback { imuData ->
                scope.launch {
                    val frame = DataFrameProtocol.createIMUFrame(imuData)
                    networkManager.sendData(frame)
                }
            }
            imuSensor.start(imuRateHz)
        }

        Logger.i(TAG, "Sensor collection started (video=$videoEnabled, imu=$imuEnabled)")
    }

    fun stop() {
        cameraManager.stopCamera()
        imuSensor.stop()
        Logger.i(TAG, "Sensor collection stopped")
    }

    fun release() {
        stop()
        cameraManager.release()
    }
}
