# 数据帧格式 (Data Frame Format)

## 二进制帧结构

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Magic Header (0xDEADBEEF)                   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Data Type    |                                               |
+-+-+-+-+-+-+-+-+                                               +
|                  Timestamp (Unix Microseconds)                 |
+               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|               |           Payload Size (bytes)                 |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Payload Data ...                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      CRC32 Checksum                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 字段说明

| Field | Size | Type | Description |
|-------|------|------|-------------|
| Magic | 4 bytes | uint32 LE | Frame identifier: 0xDEADBEEF |
| Type | 1 byte | uint8 | Data type (see table below) |
| Timestamp | 8 bytes | int64 LE | Unix timestamp in microseconds |
| Size | 4 bytes | uint32 LE | Payload size in bytes |
| Payload | N bytes | bytes | Type-specific data |
| Checksum | 4 bytes | uint32 LE | CRC32 of all preceding bytes |

**Total overhead**: 21 bytes header + 4 bytes checksum = 25 bytes

### 数据类型

| Type | Value | Description |
|------|-------|-------------|
| Camera Frame | 0x01 | YUV420/NV21 image data from camera |
| IMU Data | 0x02 | 9-DOF sensor readings |
| Pose | 0x03 | 6-DOF position and orientation |
| Map Data | 0x04 | 3D point cloud or keyframe data |

## Payload 格式

### 0x01 - Camera Frame
Raw YUV420 (NV21) image data, or H.264 encoded frame.
Size = width × height × 1.5 for NV21 format.

### 0x02 - IMU Data (36 bytes)
```
[accel_x:4B][accel_y:4B][accel_z:4B]  # m/s²
[gyro_x:4B][gyro_y:4B][gyro_z:4B]     # rad/s
[mag_x:4B][mag_y:4B][mag_z:4B]        # μT
```
All values: float32 little-endian

### 0x03 - Pose (28 bytes)
```
[pos_x:4B][pos_y:4B][pos_z:4B]        # meters
[quat_x:4B][quat_y:4B][quat_z:4B][quat_w:4B]  # unit quaternion
```
All values: float32 little-endian

### 0x04 - Map Data
Variable format: point cloud as array of (x, y, z) float32 triplets.
