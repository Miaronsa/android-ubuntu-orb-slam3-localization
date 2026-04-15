# Communication Protocol Specification

## Frame Format

```
[Header:4B][Type:1B][Timestamp:8B][Size:4B][Payload][Checksum:4B]
0xDEADBEEF  0x01-04  Unix微秒时间   字节数   实际数据  CRC32
```

All multi-byte integers are **little-endian**.

### Field Definitions

| Field     | Size    | Type      | Description                        |
|-----------|---------|-----------|------------------------------------|
| Header    | 4 bytes | uint32 LE | Magic number: `0xDEADBEEF`         |
| Type      | 1 byte  | uint8     | Data type identifier (0x01–0x04)   |
| Timestamp | 8 bytes | int64 LE  | Unix timestamp in microseconds     |
| Size      | 4 bytes | uint32 LE | Payload length in bytes            |
| Payload   | N bytes | bytes     | Type-specific payload data         |
| Checksum  | 4 bytes | uint32 LE | CRC32 over all preceding bytes     |

**Frame overhead**: 4 + 1 + 8 + 4 + 4 = **21 bytes**

---

## Data Type Definitions

| Type       | Value  | Direction         | Description              |
|------------|--------|-------------------|--------------------------|
| Camera     | `0x01` | Android → Ubuntu  | Camera image frame       |
| IMU        | `0x02` | Android → Ubuntu  | 9-DOF inertial data      |
| Pose       | `0x03` | Ubuntu → Android  | 6-DOF estimated pose     |
| Map Data   | `0x04` | Android → Ubuntu  | Map/keyframe data        |

---

## Payload Formats

### 0x01 — Camera Frame

Raw YUV420 (NV21) image data from Android CameraX.

```
[Y plane: W×H bytes][VU interleaved: W×H/2 bytes]
```

- Total size: `width × height × 1.5` bytes
- Example (640×480): 460,800 bytes

---

### 0x02 — IMU Data (36 bytes fixed)

Nine `float32` values in little-endian order:

```
Offset  Size   Field
------  -----  -------
0       4B     accel_x   (m/s²)
4       4B     accel_y   (m/s²)
8       4B     accel_z   (m/s²)
12      4B     gyro_x    (rad/s)
16      4B     gyro_y    (rad/s)
20      4B     gyro_z    (rad/s)
24      4B     mag_x     (μT)
28      4B     mag_y     (μT)
32      4B     mag_z     (μT)
```

---

### 0x03 — Pose (28 bytes fixed)

Seven `float32` values in little-endian order:

```
Offset  Size   Field
------  -----  -------
0       4B     pos_x     (meters)
4       4B     pos_y     (meters)
8       4B     pos_z     (meters)
12      4B     quat_x    (unit quaternion)
16      4B     quat_y
20      4B     quat_z
24      4B     quat_w
```

---

### 0x04 — Map Data (variable)

Array of 3D map points as `float32` triplets:

```
[x0:4B][y0:4B][z0:4B][x1:4B][y1:4B][z1:4B]...
```

Total size = `num_points × 12` bytes.

---

## Error Handling

1. **Invalid magic**: Discard frame, log warning, attempt resync.
2. **Checksum mismatch**: Discard frame, increment error counter.
3. **Truncated payload**: Wait for more data or close connection.
4. **Unknown type**: Log warning, skip frame.

---

## Examples

### Python — Parse an IMU frame

```python
import struct, zlib

MAGIC = 0xDEADBEEF
data = receive_bytes()

magic, ftype, ts, size = struct.unpack_from('<IBqI', data, 0)
assert magic == MAGIC

payload = data[17:17+size]
checksum = struct.unpack_from('<I', data, 17+size)[0]
assert zlib.crc32(data[:17+size]) & 0xFFFFFFFF == checksum

if ftype == 0x02:
    values = struct.unpack('<9f', payload)
    accel, gyro, mag = values[0:3], values[3:6], values[6:9]
```

### Kotlin — Build a camera frame

```kotlin
val buffer = ByteBuffer.allocate(21 + yuvData.size)
    .order(ByteOrder.LITTLE_ENDIAN)
buffer.putInt(0xDEADBEEF.toInt())
buffer.put(0x01)
buffer.putLong(timestampUs)
buffer.putInt(yuvData.size)
buffer.put(yuvData)
val crc = CRC32().also { it.update(buffer.array(), 0, buffer.position()) }
buffer.putInt(crc.value.toInt())
socket.outputStream.write(buffer.array())
```
