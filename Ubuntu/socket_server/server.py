"""
Async TCP socket server for receiving Android sensor data.
"""

import asyncio
import logging
from typing import Optional

from .data_handler import DataHandler

logger = logging.getLogger(__name__)


class SocketServer:
    def __init__(
        self,
        host: str = "0.0.0.0",
        port: int = 9999,
        slam_interface=None,
        rviz_publisher=None,
        database=None,
        config: dict = None
    ):
        self.host = host
        self.port = port
        self.slam_interface = slam_interface
        self.rviz_publisher = rviz_publisher
        self.database = database
        self.config = config or {}
        self.clients = {}
        self._server = None

    async def start(self) -> None:
        self._server = await asyncio.start_server(
            self._handle_client,
            self.host,
            self.port
        )
        async with self._server:
            logger.info(f"Server listening on {self.host}:{self.port}")
            await self._server.serve_forever()

    async def _handle_client(
        self,
        reader: asyncio.StreamReader,
        writer: asyncio.StreamWriter
    ) -> None:
        addr = writer.get_extra_info("peername")
        logger.info(f"New client connected: {addr}")

        handler = DataHandler(
            slam_interface=self.slam_interface,
            rviz_publisher=self.rviz_publisher,
            database=self.database
        )
        self.clients[addr] = handler

        try:
            await handler.handle(reader, writer)
        except asyncio.IncompleteReadError:
            logger.info(f"Client disconnected: {addr}")
        except Exception as e:
            logger.error(f"Client error {addr}: {e}", exc_info=True)
        finally:
            self.clients.pop(addr, None)
            writer.close()
            try:
                await writer.wait_closed()
            except Exception:
                pass
            logger.info(f"Client removed: {addr}")

    async def stop(self) -> None:
        if self._server:
            self._server.close()
            await self._server.wait_closed()
