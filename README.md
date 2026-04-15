# Android + Ubuntu ORB-SLAM3 室内定位系统

![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.8%2B-3776AB?logo=python&logoColor=white)
![ROS](https://img.shields.io/badge/ROS-Noetic-22314E?logo=ros&logoColor=white)

A real-time indoor localization system that streams camera and IMU data from an Android device to an Ubuntu server running ORB-SLAM3 for visual-inertial SLAM.

## Architecture

```
┌─────────────────────┐   TCP Socket   ┌──────────────────────────┐
│    Android Device   │ ─────────────► │    Ubuntu Server          │
│                     │                │                           │
│  CameraX (H.264)    │ ──0x01──►      │  SocketServer (asyncio)   │
│  IMU 9-DOF          │ ──0x02──►      │  ORB-SLAM3 Interface      │
│  DataCollectionSvc  │ ◄──0x03──      │  RViz Publisher (ROS)     │
│  SocketClient       │                │  SQLite Database          │
└─────────────────────┘                └──────────────────────────┘
```

## Quick Start

### Android
1. Open `Android/` in Android Studio
2. Update server IP in the app UI
3. Build & run on device (API 26+)

### Ubuntu
```bash
cd Ubuntu
pip install -r requirements.txt
python main.py --config config.yaml
```

For RViz visualization:
```bash
roscore &
rviz -d visualization/rviz_config.rviz
```

## Communication Protocol

| Type | Value | Payload |
|------|-------|---------|
| Camera Frame | `0x01` | YUV420/NV21 image |
| IMU Data | `0x02` | 9× float32 (accel+gyro+mag) |
| Pose | `0x03` | 7× float32 (xyz + quaternion) |
| Map Data | `0x04` | Point cloud bytes |

Frame format: `[Magic:4B][Type:1B][Timestamp:8B][Size:4B][Payload][CRC32:4B]`

## Directory Structure

```
├── Android/               # Android app (Kotlin + CameraX)
│   └── app/src/main/java/com/example/orblocalizer/
│       ├── sensor/        # CameraManager, IMUSensor
│       ├── network/       # SocketClient, DataFrameProtocol
│       ├── service/       # DataCollectionService
│       └── util/          # Logger, PermissionHelper
├── Ubuntu/                # Python server
│   ├── socket_server/     # AsyncIO TCP server
│   ├── orb_slam3_wrapper/ # ORB-SLAM3 interface
│   ├── visualization/     # RViz publisher
│   └── data_storage/      # SQLite database
├── Protocol/              # Protocol specification
├── Docs/                  # Documentation
├── ARCHITECTURE.md
└── PROTOCOL.md
```

## License

MIT License — see [LICENSE](LICENSE) for details.
