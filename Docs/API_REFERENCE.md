# API Reference

## Android (Kotlin)

### `CameraManager`
**Package**: `com.example.orblocalizer.sensor`

| Method | Description |
|--------|-------------|
| `setFrameCallback(callback)` | Register callback `(ByteArray, Long) -> Unit` for each YUV frame |
| `startCamera(lifecycleOwner, fps, width, height)` | Bind CameraX and begin capturing |
| `stopCamera()` | Unbind camera |
| `isRunning(): Boolean` | Whether camera is active |
| `release()` | Stop camera and shut down executor |

**Constants**: `DEFAULT_FPS = 30`, `DEFAULT_WIDTH = 640`, `DEFAULT_HEIGHT = 480`

---

### `IMUSensor`
**Package**: `com.example.orblocalizer.sensor`

| Method | Description |
|--------|-------------|
| `setIMUCallback(callback)` | Register callback `(IMUData) -> Unit` |
| `start(sampleRateHz)` | Register accelerometer, gyroscope, magnetometer |
| `stop()` | Unregister all sensors |
| `isAvailable(): Boolean` | True if accel + gyro are present |
| `isRunning(): Boolean` | Whether sensor is running |

**Data class `IMUData`**: `timestamp`, `accelX/Y/Z` (m/s²), `gyroX/Y/Z` (rad/s), `magX/Y/Z` (μT)

---

### `DataFrameProtocol`
**Package**: `com.example.orblocalizer.network`

| Method | Description |
|--------|-------------|
| `createCameraFrame(yuvData, timestampUs)` | Build binary frame for camera data |
| `createIMUFrame(imuData)` | Build binary frame for IMU data |
| `createPoseFrame(ts, x, y, z, qx, qy, qz, qw)` | Build binary frame for pose |
| `parseFrame(data): ParsedFrame?` | Parse and verify incoming frame |

**Constants**: `TYPE_CAMERA=0x01`, `TYPE_IMU=0x02`, `TYPE_POSE=0x03`, `TYPE_MAP=0x04`

---

### `SocketClient`
**Package**: `com.example.orblocalizer.network`

| Method | Description |
|--------|-------------|
| `connect()` | Start connection with auto-reconnect |
| `send(data: ByteArray): Boolean` | Enqueue data for sending |
| `disconnect()` | Close socket and cancel coroutines |
| `isConnected(): Boolean` | Current connection state |

**Callbacks**: `onConnected`, `onDisconnected`, `onError`

---

### `DataCollectionService`
**Package**: `com.example.orblocalizer.service`

Foreground service. Started via `Intent` with extras:

| Extra | Type | Default | Description |
|-------|------|---------|-------------|
| `EXTRA_SERVER_IP` | String | `192.168.1.100` | Ubuntu server IP |
| `EXTRA_SERVER_PORT` | Int | `9999` | TCP port |
| `EXTRA_CAMERA_FPS` | Int | `30` | Camera frame rate |
| `EXTRA_IMU_RATE` | Int | `100` | IMU sample rate (Hz) |
| `EXTRA_VIDEO_ENABLED` | Boolean | `true` | Enable camera streaming |
| `EXTRA_IMU_ENABLED` | Boolean | `true` | Enable IMU streaming |

---

## Ubuntu (Python)

### `SocketServer`
**Module**: `socket_server.server`

```python
server = SocketServer(host, port, slam_interface, rviz_publisher, database, config)
await server.start()   # Blocking: accepts clients until cancelled
await server.stop()    # Graceful shutdown
```

---

### `DataHandler`
**Module**: `socket_server.data_handler`

Handles one client connection. Created per-client by `SocketServer`.

```python
handler = DataHandler(slam_interface, rviz_publisher, database)
await handler.handle(reader, writer)
handler.stats  # {"frames_received": int, "errors": int}
```

---

### `ORBSLAM3Interface`
**Module**: `orb_slam3_wrapper.orbslam3_interface`

```python
slam = ORBSLAM3Interface(config)
slam.initialize()                         # Load ORB-SLAM3 (or enter simulation mode)
pose = slam.process_image(bytes, ts_us)  # Returns pose dict or None
slam.process_imu(imu_dict)               # Feed IMU measurement
slam.get_current_pose()                  # Latest pose dict
slam.get_map_points()                    # np.ndarray of 3D points
slam.reset()
slam.shutdown()
```

**Pose dict**: `{"position": [x,y,z], "quaternion": [x,y,z,w], "tracking_state": "OK"}`

---

### `RVizPublisher`
**Module**: `visualization.rviz_publisher`

```python
pub = RVizPublisher(config)
pub.initialize()                      # Connect to ROS (graceful if unavailable)
pub.publish_pose(pose_dict, ts_us)   # Publish PoseStamped + Path + TF
pub.publish_map_data(bytes, ts_us)   # Publish PointCloud2
pub.clear_path()                     # Reset trajectory history
```

---

### `Database`
**Module**: `data_storage.database`

```python
db = Database(db_path)
await db.initialize()
await db.save_frame(frame_type, timestamp, size_bytes)
await db.save_imu(imu_dict)
await db.save_pose(pose_dict)
rows = await db.get_trajectory(limit=1000)
await db.close()
```
