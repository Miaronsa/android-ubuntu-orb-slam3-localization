package com.example.orblocalizer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.orblocalizer.R
import com.example.orblocalizer.network.NetworkManager
import com.example.orblocalizer.sensor.SensorDataCollector
import com.example.orblocalizer.util.Logger

class DataCollectionService : Service() {

    companion object {
        private const val TAG = "DataCollectionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "orb_slam3_channel"

        const val EXTRA_SERVER_IP = "extra_server_ip"
        const val EXTRA_SERVER_PORT = "extra_server_port"
        const val EXTRA_CAMERA_FPS = "extra_camera_fps"
        const val EXTRA_IMU_RATE = "extra_imu_rate"
        const val EXTRA_VIDEO_ENABLED = "extra_video_enabled"
        const val EXTRA_IMU_ENABLED = "extra_imu_enabled"
    }

    inner class LocalBinder : Binder() {
        fun getService(): DataCollectionService = this@DataCollectionService
    }

    private val binder = LocalBinder()
    private lateinit var networkManager: NetworkManager
    private lateinit var sensorDataCollector: SensorDataCollector

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Logger.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverIp = intent?.getStringExtra(EXTRA_SERVER_IP) ?: "192.168.1.100"
        val serverPort = intent?.getIntExtra(EXTRA_SERVER_PORT, 9999) ?: 9999
        val cameraFps = intent?.getIntExtra(EXTRA_CAMERA_FPS, 30) ?: 30
        val imuRate = intent?.getIntExtra(EXTRA_IMU_RATE, 100) ?: 100
        val videoEnabled = intent?.getBooleanExtra(EXTRA_VIDEO_ENABLED, true) ?: true
        val imuEnabled = intent?.getBooleanExtra(EXTRA_IMU_ENABLED, true) ?: true

        startForeground(NOTIFICATION_ID, createNotification(serverIp, serverPort))

        networkManager = NetworkManager(serverIp, serverPort)
        sensorDataCollector = SensorDataCollector(applicationContext, networkManager)

        networkManager.connect()

        Logger.i(TAG, "Service started - connecting to $serverIp:$serverPort")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        if (::sensorDataCollector.isInitialized) {
            sensorDataCollector.release()
        }
        if (::networkManager.isInitialized) {
            networkManager.disconnect()
        }
        Logger.i(TAG, "Service destroyed")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ORB-SLAM3 Data Collection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Used for background data collection"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(serverIp: String, serverPort: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ORB-SLAM3 Localizer")
            .setContentText("Collecting data → $serverIp:$serverPort")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }
}
