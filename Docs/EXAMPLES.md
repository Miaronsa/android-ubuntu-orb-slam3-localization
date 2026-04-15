# Usage Examples

## Example 1: Start the Ubuntu server (simulation mode)

```bash
cd Ubuntu/
# No ORB-SLAM3 or ROS required - runs in simulation mode
python3 main.py --config config.yaml --debug
```

Expected output:
```
2024-01-01 12:00:00 [INFO] main: Starting ORB-SLAM3 Localization Server
2024-01-01 12:00:00 [WARNING] orbslam3_interface: orbslam3 Python bindings not found. Running in simulation mode.
2024-01-01 12:00:00 [INFO] main: Server starting on 0.0.0.0:9999
2024-01-01 12:00:00 [INFO] server: Server listening on 0.0.0.0:9999
```

---

## Example 2: Override config via CLI

```bash
python3 main.py --host 192.168.1.50 --port 8888 --debug
```

---

## Example 3: Connect with a test Python client

```python
import socket, struct, zlib, time

MAGIC = 0xDEADBEEF

def build_imu_frame():
    ts = int(time.time() * 1e6)
    payload = struct.pack('<9f', 0.1, 0.2, 9.8,  # accel
                                  0.0, 0.0, 0.0,  # gyro
                                  30.0, 0.0, 0.0) # mag
    header = struct.pack('<IBqI', MAGIC, 0x02, ts, len(payload))
    frame = header + payload
    checksum = zlib.crc32(frame) & 0xFFFFFFFF
    return frame + struct.pack('<I', checksum)

with socket.create_connection(('127.0.0.1', 9999)) as s:
    for _ in range(100):
        s.sendall(build_imu_frame())
        time.sleep(0.01)
print("Sent 100 IMU frames")
```

---

## Example 4: Read pose from database

```python
import asyncio
from Ubuntu.data_storage.database import Database

async def read_poses():
    db = Database("./data/localization.db")
    await db.initialize()
    poses = await db.get_trajectory(limit=10)
    for p in poses:
        print(f"t={p['timestamp']} pos=({p['pos_x']:.3f}, {p['pos_y']:.3f}, {p['pos_z']:.3f})")
    await db.close()

asyncio.run(read_poses())
```

---

## Example 5: Custom frame callback on Android

```kotlin
// In your Activity or Service
val cameraManager = CameraManager(applicationContext)
cameraManager.setFrameCallback { yuvData, timestampUs ->
    // yuvData: NV21 byte array, timestampUs: Unix microseconds
    println("Frame: ${yuvData.size} bytes at t=$timestampUs")
}
// Start camera in a coroutine
lifecycleScope.launch {
    cameraManager.startCamera(this@MyActivity, fps = 15)
}
```

---

## Example 6: Verify protocol checksum (Python)

```python
import struct, zlib

def verify_frame(data: bytes) -> bool:
    if len(data) < 21:
        return False
    magic = struct.unpack_from('<I', data, 0)[0]
    if magic != 0xDEADBEEF:
        return False
    payload_size = struct.unpack_from('<I', data, 13)[0]
    expected_len = 17 + payload_size + 4
    if len(data) < expected_len:
        return False
    received_crc = struct.unpack_from('<I', data, 17 + payload_size)[0]
    computed_crc = zlib.crc32(data[:17 + payload_size]) & 0xFFFFFFFF
    return received_crc == computed_crc
```
