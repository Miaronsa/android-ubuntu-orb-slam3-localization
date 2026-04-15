#!/usr/bin/env python3
"""
Android + Ubuntu ORB-SLAM3 Indoor Localization System
Main entry point for the Ubuntu server.
"""

import argparse
import asyncio
import logging
import os
import sys
import yaml

# Add project root to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from socket_server.server import SocketServer
from orb_slam3_wrapper.orbslam3_interface import ORBSLAM3Interface
from visualization.rviz_publisher import RVizPublisher
from data_storage.database import Database


def setup_logging(config: dict) -> None:
    log_config = config.get("logging", {})
    log_level = getattr(logging, log_config.get("level", "INFO"))
    log_file = log_config.get("file", None)

    handlers = [logging.StreamHandler()]
    if log_file:
        os.makedirs(os.path.dirname(log_file), exist_ok=True)
        handlers.append(logging.FileHandler(log_file))

    logging.basicConfig(
        level=log_level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        handlers=handlers
    )


def load_config(config_path: str) -> dict:
    with open(config_path, "r") as f:
        return yaml.safe_load(f)


async def main(config: dict) -> None:
    logger = logging.getLogger("main")
    logger.info("Starting ORB-SLAM3 Localization Server")

    # Initialize components
    db = None
    slam_interface = None
    rviz_publisher = None

    try:
        # Initialize database
        if config.get("storage", {}).get("enabled", False):
            db = Database(config["storage"]["database_path"])
            await db.initialize()
            logger.info("Database initialized")

        # Initialize ORB-SLAM3 interface
        slam_interface = ORBSLAM3Interface(config.get("orb_slam3", {}))
        if config.get("orb_slam3", {}).get("enabled", False):
            slam_interface.initialize()
            logger.info("ORB-SLAM3 initialized")

        # Initialize RViz publisher
        if config.get("visualization", {}).get("enabled", False):
            rviz_publisher = RVizPublisher(config.get("visualization", {}))
            rviz_publisher.initialize()
            logger.info("RViz publisher initialized")

        # Start socket server
        server_config = config.get("server", {})
        server = SocketServer(
            host=server_config.get("host", "0.0.0.0"),
            port=server_config.get("port", 9999),
            slam_interface=slam_interface,
            rviz_publisher=rviz_publisher,
            database=db,
            config=server_config
        )

        logger.info(f"Server starting on {server_config.get('host')}:{server_config.get('port')}")
        await server.start()

    except KeyboardInterrupt:
        logger.info("Server interrupted by user")
    except Exception as e:
        logger.error(f"Server error: {e}", exc_info=True)
        raise
    finally:
        if db:
            await db.close()
        logger.info("Server stopped")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="ORB-SLAM3 Localization Server")
    parser.add_argument("--config", default="config.yaml", help="Configuration file path")
    parser.add_argument("--host", default=None, help="Override server host")
    parser.add_argument("--port", type=int, default=None, help="Override server port")
    parser.add_argument("--debug", action="store_true", help="Enable debug logging")
    args = parser.parse_args()

    config = load_config(args.config)

    if args.debug:
        config.setdefault("logging", {})["level"] = "DEBUG"
    if args.host:
        config.setdefault("server", {})["host"] = args.host
    if args.port:
        config.setdefault("server", {})["port"] = args.port

    setup_logging(config)

    asyncio.run(main(config))
