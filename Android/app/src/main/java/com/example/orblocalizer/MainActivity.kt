package com.example.orblocalizer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.orblocalizer.service.DataCollectionService
import com.example.orblocalizer.util.Logger
import com.example.orblocalizer.util.PermissionHelper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Switch

class MainActivity : AppCompatActivity() {

    private lateinit var permissionHelper: PermissionHelper

    private var dataCollectionService: DataCollectionService? = null
    private var serviceBound = false
    private var isCollecting = false

    // UI elements (referenced by ID from layout)
    private lateinit var etServerIp: EditText
    private lateinit var etServerPort: EditText
    private lateinit var etCameraFps: EditText
    private lateinit var etImuRate: EditText
    private lateinit var btnToggleCollection: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var switchVideoEnabled: Switch
    private lateinit var switchImuEnabled: Switch

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DataCollectionService.LocalBinder
            dataCollectionService = binder.getService()
            serviceBound = true
            Logger.d("MainActivity", "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dataCollectionService = null
            serviceBound = false
            Logger.d("MainActivity", "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionHelper = PermissionHelper(this)

        initViews()
        setupClickListeners()

        if (!permissionHelper.hasAllPermissions()) {
            permissionHelper.requestPermissions()
        }
    }

    private fun initViews() {
        etServerIp = findViewById(R.id.et_server_ip)
        etServerPort = findViewById(R.id.et_server_port)
        etCameraFps = findViewById(R.id.et_camera_fps)
        etImuRate = findViewById(R.id.et_imu_rate)
        btnToggleCollection = findViewById(R.id.btn_toggle_collection)
        tvStatus = findViewById(R.id.tv_status)
        tvConnectionStatus = findViewById(R.id.tv_connection_status)
        switchVideoEnabled = findViewById(R.id.switch_video_enabled)
        switchImuEnabled = findViewById(R.id.switch_imu_enabled)

        // Set default values
        etServerIp.setText("192.168.1.100")
        etServerPort.setText("9999")
        etCameraFps.setText("30")
        etImuRate.setText("100")
    }

    private fun setupClickListeners() {
        btnToggleCollection.setOnClickListener {
            if (!permissionHelper.hasAllPermissions()) {
                permissionHelper.requestPermissions()
                return@setOnClickListener
            }
            if (isCollecting) {
                stopCollection()
            } else {
                startCollection()
            }
        }
    }

    private fun startCollection() {
        val serverIp = etServerIp.text.toString().trim()
        val serverPort = etServerPort.text.toString().toIntOrNull() ?: 9999
        val cameraFps = etCameraFps.text.toString().toIntOrNull() ?: 30
        val imuRate = etImuRate.text.toString().toIntOrNull() ?: 100
        val videoEnabled = switchVideoEnabled.isChecked
        val imuEnabled = switchImuEnabled.isChecked

        if (serverIp.isEmpty()) {
            Toast.makeText(this, "Please enter server IP", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, DataCollectionService::class.java).apply {
            putExtra(DataCollectionService.EXTRA_SERVER_IP, serverIp)
            putExtra(DataCollectionService.EXTRA_SERVER_PORT, serverPort)
            putExtra(DataCollectionService.EXTRA_CAMERA_FPS, cameraFps)
            putExtra(DataCollectionService.EXTRA_IMU_RATE, imuRate)
            putExtra(DataCollectionService.EXTRA_VIDEO_ENABLED, videoEnabled)
            putExtra(DataCollectionService.EXTRA_IMU_ENABLED, imuEnabled)
        }

        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        isCollecting = true
        btnToggleCollection.text = "Stop Collection"
        tvStatus.text = "Status: Collecting..."
        tvConnectionStatus.text = "Connecting to $serverIp:$serverPort..."

        Logger.i("MainActivity", "Started collection to $serverIp:$serverPort")
    }

    private fun stopCollection() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }

        val intent = Intent(this, DataCollectionService::class.java)
        stopService(intent)

        isCollecting = false
        btnToggleCollection.text = "Start Collection"
        tvStatus.text = "Status: Stopped"
        tvConnectionStatus.text = "Disconnected"

        Logger.i("MainActivity", "Stopped collection")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper.onRequestPermissionsResult(requestCode, grantResults) { allGranted ->
            if (!allGranted) {
                Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
