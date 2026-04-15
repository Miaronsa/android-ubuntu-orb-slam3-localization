"""
SQLite database for persisting localization data.
"""

import asyncio
import logging
import os
import sqlite3
from typing import Dict, List, Optional

logger = logging.getLogger(__name__)


class Database:
    def __init__(self, db_path: str = "./data/localization.db"):
        self.db_path = db_path
        self._conn: Optional[sqlite3.Connection] = None
        self._lock = asyncio.Lock()

    async def initialize(self) -> None:
        os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        self._conn = sqlite3.connect(self.db_path, check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        await self._create_tables()
        logger.info(f"Database initialized at {self.db_path}")

    async def _create_tables(self) -> None:
        async with self._lock:
            cursor = self._conn.cursor()
            cursor.executescript("""
                CREATE TABLE IF NOT EXISTS camera_frames (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    size_bytes INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE IF NOT EXISTS imu_data (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    accel_x REAL, accel_y REAL, accel_z REAL,
                    gyro_x REAL, gyro_y REAL, gyro_z REAL,
                    mag_x REAL, mag_y REAL, mag_z REAL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE IF NOT EXISTS poses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    pos_x REAL, pos_y REAL, pos_z REAL,
                    quat_x REAL, quat_y REAL, quat_z REAL, quat_w REAL,
                    tracking_state TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );

                CREATE INDEX IF NOT EXISTS idx_camera_timestamp ON camera_frames(timestamp);
                CREATE INDEX IF NOT EXISTS idx_imu_timestamp ON imu_data(timestamp);
                CREATE INDEX IF NOT EXISTS idx_pose_timestamp ON poses(timestamp);
            """)
            self._conn.commit()

    async def save_frame(self, frame_type: str, timestamp: int, size_bytes: int) -> None:
        async with self._lock:
            cursor = self._conn.cursor()
            cursor.execute(
                "INSERT INTO camera_frames (timestamp, size_bytes) VALUES (?, ?)",
                (timestamp, size_bytes)
            )
            self._conn.commit()

    async def save_imu(self, imu_data: Dict) -> None:
        async with self._lock:
            cursor = self._conn.cursor()
            accel = imu_data.get("accel", [0, 0, 0])
            gyro = imu_data.get("gyro", [0, 0, 0])
            mag = imu_data.get("mag", [0, 0, 0])
            cursor.execute(
                """INSERT INTO imu_data
                   (timestamp, accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z,
                    mag_x, mag_y, mag_z)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (imu_data["timestamp"], *accel, *gyro, *mag)
            )
            self._conn.commit()

    async def save_pose(self, pose: Dict) -> None:
        async with self._lock:
            cursor = self._conn.cursor()
            pos = pose.get("position", [0, 0, 0])
            quat = pose.get("quaternion", [0, 0, 0, 1])
            cursor.execute(
                """INSERT INTO poses
                   (timestamp, pos_x, pos_y, pos_z, quat_x, quat_y, quat_z, quat_w,
                    tracking_state)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    pose.get("timestamp", 0),
                    *pos, *quat,
                    pose.get("tracking_state", "UNKNOWN")
                )
            )
            self._conn.commit()

    async def get_trajectory(self, limit: int = 1000) -> List[Dict]:
        async with self._lock:
            cursor = self._conn.cursor()
            cursor.execute(
                "SELECT * FROM poses ORDER BY timestamp DESC LIMIT ?",
                (limit,)
            )
            return [dict(row) for row in cursor.fetchall()]

    async def close(self) -> None:
        if self._conn:
            self._conn.close()
            self._conn = None
        logger.info("Database connection closed")
