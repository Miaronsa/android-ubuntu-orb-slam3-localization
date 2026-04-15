"""
RViz publisher for ORB-SLAM3 pose and map visualization via ROS.
"""

import logging
import math
from typing import Dict, List, Optional

logger = logging.getLogger(__name__)


class RVizPublisher:
    """
    Publishes ORB-SLAM3 data to RViz via ROS topics.

    Topics published:
    - /orb_slam3/pose (geometry_msgs/PoseStamped)
    - /orb_slam3/path (nav_msgs/Path)
    - /orb_slam3/map_points (sensor_msgs/PointCloud2)
    - /tf (camera → world transform)
    """

    def __init__(self, config: Dict = None):
        self.config = config or {}
        self._ros_available = False
        self._node = None
        self._publishers = {}
        self._path_poses = []

        self.map_frame = config.get("map_frame", "world") if config else "world"
        self.camera_frame = config.get("camera_frame", "camera") if config else "camera"

    def initialize(self) -> bool:
        try:
            import rospy
            from geometry_msgs.msg import PoseStamped
            from nav_msgs.msg import Path
            from sensor_msgs.msg import PointCloud2
            import tf

            rospy.init_node("orb_slam3_publisher", anonymous=True, disable_signals=True)

            self._publishers["pose"] = rospy.Publisher(
                "/orb_slam3/pose", PoseStamped, queue_size=10
            )
            self._publishers["path"] = rospy.Publisher(
                "/orb_slam3/path", Path, queue_size=10
            )
            self._publishers["map_points"] = rospy.Publisher(
                "/orb_slam3/map_points", PointCloud2, queue_size=10
            )
            self._tf_broadcaster = tf.TransformBroadcaster()
            self._ros_available = True
            logger.info("RViz publisher initialized successfully")
            return True

        except ImportError:
            logger.warning(
                "ROS not available. RViz publishing disabled. "
                "Install ROS Noetic for visualization."
            )
            self._ros_available = False
            return False
        except Exception as e:
            logger.error(f"RViz init error: {e}")
            return False

    def publish_pose(self, pose: Dict, timestamp_us: int) -> None:
        if not self._ros_available:
            return
        try:
            import rospy
            from geometry_msgs.msg import PoseStamped
            from nav_msgs.msg import Path

            timestamp_s = timestamp_us / 1e6
            ros_time = rospy.Time.from_sec(timestamp_s)

            pose_msg = PoseStamped()
            pose_msg.header.stamp = ros_time
            pose_msg.header.frame_id = self.map_frame

            position = pose.get("position", [0, 0, 0])
            quaternion = pose.get("quaternion", [0, 0, 0, 1])

            pose_msg.pose.position.x = position[0]
            pose_msg.pose.position.y = position[1]
            pose_msg.pose.position.z = position[2]
            pose_msg.pose.orientation.x = quaternion[0]
            pose_msg.pose.orientation.y = quaternion[1]
            pose_msg.pose.orientation.z = quaternion[2]
            pose_msg.pose.orientation.w = quaternion[3]

            self._publishers["pose"].publish(pose_msg)

            # Update and publish path
            self._path_poses.append(pose_msg)
            if len(self._path_poses) > 10000:
                self._path_poses.pop(0)

            path_msg = Path()
            path_msg.header.stamp = ros_time
            path_msg.header.frame_id = self.map_frame
            path_msg.poses = self._path_poses
            self._publishers["path"].publish(path_msg)

            # Publish TF
            self._tf_broadcaster.sendTransform(
                (position[0], position[1], position[2]),
                (quaternion[0], quaternion[1], quaternion[2], quaternion[3]),
                ros_time,
                self.camera_frame,
                self.map_frame
            )

        except Exception as e:
            logger.debug(f"Pose publish error: {e}")

    def publish_map_data(self, map_data: bytes, timestamp_us: int) -> None:
        if not self._ros_available:
            return
        logger.debug(f"Map data received: {len(map_data)} bytes at t={timestamp_us}")

    def clear_path(self) -> None:
        self._path_poses.clear()
