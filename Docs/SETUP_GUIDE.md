# Setup Guide

## Prerequisites

- Android device with API 26+ (Android 8.0 Oreo), camera and IMU sensors
- Ubuntu 20.04 LTS with ROS Noetic (for visualization)
- Python 3.8+
- Android Studio Hedgehog or newer
- Both devices on the same local network (Wi-Fi)

---

## 1. Android Setup

### 1.1 Install Android Studio

Download from https://developer.android.com/studio and install.

### 1.2 Open the Project

```bash
# Open Android/ directory in Android Studio
File → Open → select the Android/ folder
```

### 1.3 Configure the App

Edit the server IP in the app UI at runtime, or change the default:

```kotlin
// Android/app/src/main/java/com/example/orblocalizer/MainActivity.kt
etServerIp.setText("192.168.YOUR.IP")
```

### 1.4 Enable Developer Options on Device

1. Settings → About Phone → tap Build Number 7 times
2. Settings → Developer Options → enable USB Debugging

### 1.5 Build & Run

Click **Run ▶** in Android Studio with device connected via USB.

Required permissions will be requested on first launch:
- Camera
- Internet
- POST_NOTIFICATIONS (Android 13+)

---

## 2. Ubuntu Server Setup

### 2.1 Install Python Dependencies

```bash
cd Ubuntu/
pip3 install -r requirements.txt
```

### 2.2 Install ROS Noetic (Optional, for visualization)

```bash
# Add ROS repository
sudo sh -c 'echo "deb http://packages.ros.org/ros/ubuntu focal main" > /etc/apt/sources.list.d/ros-latest.list'
sudo apt-key adv --keyserver 'hkp://keyserver.ubuntu.com:80' --recv-key C1CF6E31E6BADE8868B172B4F42ED6FBAB17C654
sudo apt update
sudo apt install ros-noetic-desktop-full

# Source ROS
echo "source /opt/ros/noetic/setup.bash" >> ~/.bashrc
source ~/.bashrc
```

### 2.3 Configure Server

Edit `Ubuntu/config.yaml`:

```yaml
server:
  host: "0.0.0.0"   # Listen on all interfaces
  port: 9999

orb_slam3:
  enabled: false     # Set true when ORB-SLAM3 is built

visualization:
  enabled: false     # Set true when ROS is installed
```

### 2.4 Start the Server

```bash
cd Ubuntu/
python3 main.py --config config.yaml
```

To enable debug logging:
```bash
python3 main.py --debug
```

---

## 3. ORB-SLAM3 Compilation (Optional)

### 3.1 Install Dependencies

```bash
sudo apt install libopencv-dev libeigen3-dev libpangolin-dev
```

### 3.2 Clone and Build ORB-SLAM3

```bash
git clone https://github.com/UZ-SLAMLab/ORB_SLAM3.git
cd ORB_SLAM3
chmod +x build.sh
./build.sh
```

### 3.3 Update Config

In `Ubuntu/config.yaml`, set the correct paths:

```yaml
orb_slam3:
  enabled: true
  vocabulary_path: "/path/to/ORB_SLAM3/Vocabulary/ORBvoc.txt"
  config_path: "orb_slam3_wrapper/config/orb_slam3_config.yaml"
```

---

## 4. RViz Visualization

### 4.1 Start ROS Core

```bash
roscore &
```

### 4.2 Open RViz with Config

```bash
rviz -d Ubuntu/visualization/rviz_config.rviz
```

### 4.3 Topics

| Topic | Type | Description |
|-------|------|-------------|
| `/orb_slam3/pose` | PoseStamped | Current camera pose |
| `/orb_slam3/path` | Path | Camera trajectory |
| `/orb_slam3/map_points` | PointCloud2 | 3D map points |

---

## 5. Running the Full System

1. Start Ubuntu server: `python3 Ubuntu/main.py`
2. Start ROS core + RViz (optional)
3. Launch Android app, enter Ubuntu machine's IP
4. Tap **Start Collection**

The Android app will connect and begin streaming camera frames and IMU data.
