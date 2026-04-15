"""
Handles parsing and routing of incoming data frames from Android client.
"""

import asyncio
import logging
import struct
import zlib
from typing import Optional, Tuple

logger = logging.getLogger(__name__)

# Frame protocol constants
FRAME_MAGIC = 0xDEADBEEF
FRAME_HEADER_SIZE = 17  # 4 (magic) + 1 (type) + 8 (timestamp) + 4 (size)
FRAME_CHECKSUM_SIZE = 4
FRAME_OVERHEAD = FRAME_HEADER_SIZE + FRAME_CHECKSUM_SIZE

# Data types
TYPE_CAMERA = 0x01
TYPE_IMU = 0x02
TYPE_POSE = 0x03
TYPE_MAP = 0x04


class DataHandler:
    def __init__(self, slam_interface=None, rviz_publisher=None, database=None):
        self.slam_interface = slam_interface
        self.rviz_publisher = rviz_publisher
        self.database = database
        self._frame_count = 0
        self._error_count = 0

    async def handle(
        self,
        reader: asyncio.StreamReader,
        writer: asyncio.StreamWriter
    ) -> None:
        while True:
            frame = await self._read_frame(reader)
            if frame is None:
                break
            await self._route_frame(frame, writer)

    async def _read_frame(self, reader: asyncio.StreamReader) -> Optional[dict]:
        try:
            # Read fixed header
            header_data = await reader.readexactly(FRAME_HEADER_SIZE)
            magic, frame_type, timestamp, payload_size = struct.unpack_from(
                "<IbQI", header_data
            )

            if magic != FRAME_MAGIC:
                logger.warning(f"Invalid magic: 0x{magic:08X}")
                self._error_count += 1
                return None

            # Read payload
            payload = await reader.readexactly(payload_size)

            # Read checksum
            checksum_data = await reader.readexactly(FRAME_CHECKSUM_SIZE)
            received_checksum = struct.unpack("<I", checksum_data)[0]

            # Verify checksum
            frame_data = header_data + payload
            expected_checksum = zlib.crc32(frame_data) & 0xFFFFFFFF
            if received_checksum != expected_checksum:
                logger.warning("Checksum mismatch, dropping frame")
                self._error_count += 1
                return None

            self._frame_count += 1
            return {
                "type": frame_type,
                "timestamp": timestamp,
                "payload": payload
            }

        except asyncio.IncompleteReadError:
            raise
        except Exception as e:
            logger.error(f"Frame read error: {e}")
            self._error_count += 1
            return None

    async def _route_frame(self, frame: dict, writer: asyncio.StreamWriter) -> None:
        frame_type = frame["type"]
        timestamp = frame["timestamp"]
        payload = frame["payload"]

        try:
            if frame_type == TYPE_CAMERA:
                await self._handle_camera_frame(timestamp, payload, writer)
            elif frame_type == TYPE_IMU:
                await self._handle_imu_frame(timestamp, payload)
            elif frame_type == TYPE_POSE:
                await self._handle_pose_frame(timestamp, payload)
            elif frame_type == TYPE_MAP:
                await self._handle_map_frame(timestamp, payload)
            else:
                logger.warning(f"Unknown frame type: 0x{frame_type:02X}")
        except Exception as e:
            logger.error(f"Frame routing error (type=0x{frame_type:02X}): {e}")

    async def _handle_camera_frame(
        self,
        timestamp: int,
        payload: bytes,
        writer: asyncio.StreamWriter
    ) -> None:
        if self.slam_interface:
            pose = await asyncio.get_event_loop().run_in_executor(
                None,
                self.slam_interface.process_image,
                payload,
                timestamp
            )
            if pose is not None and self.rviz_publisher:
                self.rviz_publisher.publish_pose(pose, timestamp)

        if self.database:
            await self.database.save_frame("camera", timestamp, len(payload))

    async def _handle_imu_frame(self, timestamp: int, payload: bytes) -> None:
        if len(payload) < 36:  # 9 floats * 4 bytes
            logger.warning(f"IMU payload too small: {len(payload)}")
            return

        values = struct.unpack("<9f", payload[:36])
        imu_data = {
            "timestamp": timestamp,
            "accel": values[0:3],
            "gyro": values[3:6],
            "mag": values[6:9]
        }

        if self.slam_interface:
            await asyncio.get_event_loop().run_in_executor(
                None,
                self.slam_interface.process_imu,
                imu_data
            )

        if self.database:
            await self.database.save_imu(imu_data)

    async def _handle_pose_frame(self, timestamp: int, payload: bytes) -> None:
        if len(payload) < 28:  # 7 floats * 4 bytes
            return
        values = struct.unpack("<7f", payload[:28])
        pose = {
            "timestamp": timestamp,
            "position": values[0:3],
            "quaternion": values[3:7]
        }
        if self.rviz_publisher:
            self.rviz_publisher.publish_pose(pose, timestamp)

    async def _handle_map_frame(self, timestamp: int, payload: bytes) -> None:
        if self.rviz_publisher:
            self.rviz_publisher.publish_map_data(payload, timestamp)

    @property
    def stats(self) -> dict:
        return {
            "frames_received": self._frame_count,
            "errors": self._error_count
        }
