# Troubleshooting Guide

## Android Issues

### App crashes immediately on launch
- Ensure Camera permission is granted: Settings → Apps → ORB-SLAM3 Localizer → Permissions
- Check device is API 26+ (Android 8.0)

### "Required permissions not granted"
- Go to device Settings → Apps → grant Camera and Notifications permissions manually

### Camera fails to start
- Close other apps using the camera (Google Meet, camera app, etc.)
- Restart the app

### "Send queue full, dropping frame"
- Network bandwidth is too low for the configured FPS
- Reduce Camera FPS in the app (try 15 instead of 30)
- Or reduce resolution in `CameraManager.DEFAULT_WIDTH/HEIGHT`

### Cannot connect to server
1. Verify Android and Ubuntu are on the same Wi-Fi network
2. Check Ubuntu firewall: `sudo ufw allow 9999/tcp`
3. Ping the Ubuntu machine from Android (use a terminal app)
4. Verify server is running: `netstat -tlnp | grep 9999`

---

## Ubuntu Server Issues

### `ModuleNotFoundError: No module named 'yaml'`
```bash
pip3 install pyyaml
```

### `ModuleNotFoundError: No module named 'cv2'`
```bash
pip3 install opencv-python
```

### Server starts but no clients connect
- Check firewall: `sudo ufw status` — port 9999 must be allowed
- Verify server is listening: `ss -tlnp | grep 9999`
- Confirm Android app IP matches server IP

### "orbslam3 Python bindings not found"
This is expected if ORB-SLAM3 is not compiled. The server runs in simulation mode.
To enable real SLAM, build ORB-SLAM3 and its Python bindings — see `SETUP_GUIDE.md`.

### Database initialization fails
```
PermissionError: [Errno 13] Permission denied: './data/localization.db'
```
Solution: `mkdir -p Ubuntu/data && chmod 755 Ubuntu/data`

---

## ROS / RViz Issues

### "ROS not available. RViz publishing disabled."
Install ROS Noetic: see `SETUP_GUIDE.md` Section 2.2.

### RViz shows no data
1. Confirm `roscore` is running: `rostopic list`
2. Check topics: `rostopic echo /orb_slam3/pose`
3. Verify Fixed Frame is set to `world` in RViz Global Options

### TF transform warnings in RViz
- The `world → camera` TF is only published when poses are received
- Ensure ORB-SLAM3 or simulation mode is producing poses

---

## Performance Tuning

| Issue | Solution |
|-------|----------|
| High latency | Reduce FPS or image resolution |
| SLAM drift | Calibrate camera intrinsics for your device |
| IMU noise | Tune `accel_noise_density` / `gyro_noise_density` in config.yaml |
| Frame drops on server | Increase `buffer_size` in config.yaml |
