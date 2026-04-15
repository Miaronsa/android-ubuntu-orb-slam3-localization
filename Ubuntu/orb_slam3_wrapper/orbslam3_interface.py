"""
ORB-SLAM3 Python interface wrapper.
Provides a clean API for interacting with ORB-SLAM3 from Python.
"""

import logging
import numpy as np
from typing import Optional, Dict, Any

logger = logging.getLogger(__name__)


class ORBSLAM3Interface:
    """
    Interface to ORB-SLAM3 SLAM system.

    This class wraps the ORB-SLAM3 C++ library (via Python bindings or subprocess)
    and provides a clean async-compatible interface.

    To use with actual ORB-SLAM3:
    1. Build ORB-SLAM3 with Python bindings (or use ctypes/subprocess)
    2. Set the vocabulary_path and config_path in config
    3. Call initialize() before processing frames
    """

    MODES = {
        "MONO": 0,
        "STEREO": 1,
        "RGBD": 2,
        "VI_MONO": 3,
        "VI_STEREO": 4,
    }

    def __init__(self, config: Dict[str, Any] = None):
        self.config = config or {}
        self._slam_system = None
        self._initialized = False
        self._frame_count = 0
        self._current_pose = None

    def initialize(self) -> bool:
        vocab_path = self.config.get("vocabulary_path", "")
        config_path = self.config.get("config_path", "")
        mode_str = self.config.get("mode", "VI_MONO")
        mode = self.MODES.get(mode_str, 3)

        logger.info(f"Initializing ORB-SLAM3 (mode={mode_str})")
        logger.info(f"  Vocabulary: {vocab_path}")
        logger.info(f"  Config: {config_path}")

        try:
            # Attempt to import orbslam3 Python bindings
            # This requires ORB-SLAM3 to be built with Python support
            import orbslam3  # type: ignore
            self._slam_system = orbslam3.System(vocab_path, config_path, mode)
            self._initialized = True
            logger.info("ORB-SLAM3 initialized successfully")
            return True
        except ImportError:
            logger.warning(
                "orbslam3 Python bindings not found. "
                "Running in simulation mode. "
                "Build ORB-SLAM3 with Python bindings for full functionality."
            )
            self._initialized = True  # Allow simulation mode
            return False
        except Exception as e:
            logger.error(f"Failed to initialize ORB-SLAM3: {e}")
            return False

    def process_image(self, image_data: bytes, timestamp_us: int) -> Optional[Dict]:
        if not self._initialized:
            return None

        try:
            import cv2
            timestamp_s = timestamp_us / 1e6

            # Decode image (assuming YUV420 NV21 format from Android)
            nparr = np.frombuffer(image_data, np.uint8)
            if len(nparr) > 0:
                # For simulation: return a dummy pose
                if self._slam_system is None:
                    return self._simulate_pose(timestamp_s)

                # Real ORB-SLAM3 processing
                gray_image = cv2.imdecode(nparr, cv2.IMREAD_GRAYSCALE)
                if gray_image is None:
                    return None

                pose_matrix = self._slam_system.TrackMonocular(gray_image, timestamp_s)
                if pose_matrix is not None:
                    self._current_pose = self._matrix_to_pose(pose_matrix)
                    self._frame_count += 1
                    return self._current_pose

        except Exception as e:
            logger.debug(f"Image processing error: {e}")
        return None

    def process_imu(self, imu_data: Dict) -> None:
        if not self._initialized or self._slam_system is None:
            return
        # ORB-SLAM3 IMU data format: [(acc_x, acc_y, acc_z, gyr_x, gyr_y, gyr_z, timestamp)]
        # This would be buffered and used in the next TrackMonocular call

    def get_current_pose(self) -> Optional[Dict]:
        return self._current_pose

    def get_map_points(self) -> Optional[np.ndarray]:
        if self._slam_system is None:
            return None
        try:
            return self._slam_system.GetTrackedMapPoints()
        except Exception:
            return None

    def reset(self) -> None:
        if self._slam_system:
            try:
                self._slam_system.Reset()
            except Exception as e:
                logger.error(f"Reset error: {e}")
        self._frame_count = 0
        self._current_pose = None

    def shutdown(self) -> None:
        if self._slam_system:
            try:
                self._slam_system.Shutdown()
            except Exception as e:
                logger.error(f"Shutdown error: {e}")
        self._initialized = False
        logger.info("ORB-SLAM3 shutdown")

    def _simulate_pose(self, timestamp_s: float) -> Dict:
        """Return a simulated pose for testing without real ORB-SLAM3."""
        import math
        t = timestamp_s * 0.1
        return {
            "position": [math.sin(t) * 0.5, 0.0, math.cos(t) * 0.5],
            "quaternion": [0.0, math.sin(t * 0.5), 0.0, math.cos(t * 0.5)],
            "timestamp": timestamp_s,
            "tracking_state": "OK"
        }

    def _matrix_to_pose(self, matrix: np.ndarray) -> Dict:
        """Convert 4x4 transformation matrix to position + quaternion."""
        if matrix is None or matrix.shape != (4, 4):
            return None
        position = matrix[:3, 3].tolist()
        # Rotation matrix to quaternion
        r = matrix[:3, :3]
        trace = r[0, 0] + r[1, 1] + r[2, 2]
        if trace > 0:
            s = 0.5 / np.sqrt(trace + 1.0)
            w = 0.25 / s
            x = (r[2, 1] - r[1, 2]) * s
            y = (r[0, 2] - r[2, 0]) * s
            z = (r[1, 0] - r[0, 1]) * s
        else:
            w, x, y, z = 1.0, 0.0, 0.0, 0.0
        return {
            "position": position,
            "quaternion": [x, y, z, w],
            "tracking_state": "OK"
        }

    @property
    def is_initialized(self) -> bool:
        return self._initialized

    @property
    def frame_count(self) -> int:
        return self._frame_count
