package com.example.orblocalizer.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.orblocalizer.util.Logger

data class IMUData(
    val timestamp: Long,           // microseconds
    val accelX: Float,             // m/s²
    val accelY: Float,             // m/s²
    val accelZ: Float,             // m/s²
    val gyroX: Float,              // rad/s
    val gyroY: Float,              // rad/s
    val gyroZ: Float,              // rad/s
    val magX: Float,               // μT
    val magY: Float,               // μT
    val magZ: Float                // μT
)

class IMUSensor(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "IMUSensor"
        const val DEFAULT_SAMPLE_RATE_HZ = 100
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var accelData = FloatArray(3)
    private var gyroData = FloatArray(3)
    private var magData = FloatArray(3)

    private var imuCallback: ((IMUData) -> Unit)? = null
    private var isRunning = false

    fun setIMUCallback(callback: (IMUData) -> Unit) {
        imuCallback = callback
    }

    fun start(sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ) {
        val periodUs = 1_000_000 / sampleRateHz

        accelerometer?.let {
            sensorManager.registerListener(this, it, periodUs)
            Logger.d(TAG, "Accelerometer registered at ${sampleRateHz}Hz")
        } ?: Logger.w(TAG, "Accelerometer not available")

        gyroscope?.let {
            sensorManager.registerListener(this, it, periodUs)
            Logger.d(TAG, "Gyroscope registered at ${sampleRateHz}Hz")
        } ?: Logger.w(TAG, "Gyroscope not available")

        magnetometer?.let {
            sensorManager.registerListener(this, it, periodUs)
            Logger.d(TAG, "Magnetometer registered at ${sampleRateHz}Hz")
        } ?: Logger.w(TAG, "Magnetometer not available")

        isRunning = true
        Logger.i(TAG, "IMU sensor started at ${sampleRateHz}Hz")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        isRunning = false
        Logger.i(TAG, "IMU sensor stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        val timestampUs = System.currentTimeMillis() * 1000L

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelData = event.values.clone()
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroData = event.values.clone()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magData = event.values.clone()
                // Publish combined IMU data when magnetometer updates
                val imuData = IMUData(
                    timestamp = timestampUs,
                    accelX = accelData[0],
                    accelY = accelData[1],
                    accelZ = accelData[2],
                    gyroX = gyroData[0],
                    gyroY = gyroData[1],
                    gyroZ = gyroData[2],
                    magX = magData[0],
                    magY = magData[1],
                    magZ = magData[2]
                )
                imuCallback?.invoke(imuData)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Logger.d(TAG, "Sensor accuracy changed: ${sensor?.name} = $accuracy")
    }

    fun isAvailable(): Boolean {
        return accelerometer != null && gyroscope != null
    }

    fun isRunning(): Boolean = isRunning
}
