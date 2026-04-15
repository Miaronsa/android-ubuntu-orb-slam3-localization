# 系统架构设计 (System Architecture)

## 总体架构

本系统由 Android 移动端和 Ubuntu 服务端两部分组成，通过 TCP Socket 实现实时数据传输。

```
┌─────────────────────────────────┐     TCP Socket      ┌──────────────────────────────────┐
│         Android 端               │ ─────────────────► │         Ubuntu 端                 │
│                                 │                     │                                  │
│  ┌───────────────────────────┐  │   Camera Frame      │  ┌────────────────────────────┐  │
│  │      CameraManager        │  │ ──── 0x01 ────►    │  │     Socket Server           │  │
│  │  (CameraX + H.264)        │  │                     │  │   (AsyncIO Multi-client)    │  │
│  └───────────────────────────┘  │   IMU Data          │  └────────────────────────────┘  │
│  ┌───────────────────────────┐  │ ──── 0x02 ────►    │                │                 │
│  │       IMUSensor           │  │                     │                ▼                 │
│  │  (9-DOF: Acc+Gyro+Mag)    │  │   Pose Feedback     │  ┌────────────────────────────┐  │
│  └───────────────────────────┘  │ ◄─── 0x03 ────     │  │     Data Handler            │  │
│  ┌───────────────────────────┐  │                     │  │  (CRC32 + Type Routing)     │  │
│  │      SocketClient         │  │   Map Data          │  └────────────────────────────┘  │
│  │  (TCP + Auto-reconnect)   │  │ ─── 0x04 ────►    │                │                 │
│  └───────────────────────────┘  │                     │                ▼                 │
│  ┌───────────────────────────┐  │                     │  ┌────────────────────────────┐  │
│  │   DataCollectionService   │  │                     │  │   ORB-SLAM3 Interface       │  │
│  │  (Foreground Service)     │  │                     │  │  (Visual-Inertial SLAM)     │  │
│  └───────────────────────────┘  │                     │  └────────────────────────────┘  │
└─────────────────────────────────┘                     │                │                 │
                                                        │                ▼                 │
                                                        │  ┌────────────────────────────┐  │
                                                        │  │     RViz Publisher          │  │
                                                        │  │  (TF + Trajectory + Map)   │  │
                                                        │  └────────────────────────────┘  │
                                                        └──────────────────────────────────┘
```

## 数据流

1. **Android 相机** → H.264 编码 → TCP → Ubuntu 服务器 → ORB-SLAM3 图像处理
2. **Android IMU** → 9-DOF 数据 → TCP → Ubuntu 服务器 → ORB-SLAM3 IMU 融合
3. **ORB-SLAM3** → 位姿估计 → TCP → Android 端（可选反馈）
4. **ORB-SLAM3** → 地图点云 → RViz 可视化

## 关键技术

- **CameraX**: Android Jetpack 相机库，支持 H.264 硬件编码
- **IMU 融合**: 加速度计 + 陀螺仪 + 磁力计 9-DOF 融合
- **ORB-SLAM3**: 最先进的视觉-惯性 SLAM 系统
- **ROS Noetic**: 机器人操作系统，用于可视化和模块通信
- **AsyncIO**: Python 异步 I/O，高效处理多客户端连接
