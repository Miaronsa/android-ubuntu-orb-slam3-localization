"""
Pose estimation utilities for ORB-SLAM3 output processing.
"""

import logging
import numpy as np
from typing import Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)


class PoseEstimator:
    def __init__(self):
        self._pose_history: List[Dict] = []
        self._max_history = 1000

    def update(self, pose: Dict) -> None:
        self._pose_history.append(pose)
        if len(self._pose_history) > self._max_history:
            self._pose_history.pop(0)

    def get_trajectory(self) -> List[List[float]]:
        return [p["position"] for p in self._pose_history if "position" in p]

    def get_latest_pose(self) -> Optional[Dict]:
        return self._pose_history[-1] if self._pose_history else None

    def get_velocity(self) -> Optional[np.ndarray]:
        if len(self._pose_history) < 2:
            return None
        p1 = np.array(self._pose_history[-2]["position"])
        p2 = np.array(self._pose_history[-1]["position"])
        dt = (
            self._pose_history[-1].get("timestamp", 0) -
            self._pose_history[-2].get("timestamp", 0)
        )
        if dt <= 0:
            return None
        return (p2 - p1) / dt

    def euler_from_quaternion(self, q: List[float]) -> Tuple[float, float, float]:
        """Convert quaternion (x, y, z, w) to Euler angles (roll, pitch, yaw) in radians."""
        x, y, z, w = q
        roll = np.arctan2(2 * (w * x + y * z), 1 - 2 * (x**2 + y**2))
        pitch = np.arcsin(np.clip(2 * (w * y - z * x), -1, 1))
        yaw = np.arctan2(2 * (w * z + x * y), 1 - 2 * (y**2 + z**2))
        return roll, pitch, yaw

    def reset(self) -> None:
        self._pose_history.clear()
