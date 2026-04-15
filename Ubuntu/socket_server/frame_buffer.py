"""
Thread-safe frame buffer for managing incoming data frames.
"""

import asyncio
import logging
from collections import deque
from typing import Optional

logger = logging.getLogger(__name__)


class FrameBuffer:
    def __init__(self, max_size: int = 100):
        self.max_size = max_size
        self._buffer = deque(maxlen=max_size)
        self._lock = asyncio.Lock()
        self._event = asyncio.Event()

    async def put(self, frame: dict) -> bool:
        async with self._lock:
            if len(self._buffer) >= self.max_size:
                logger.warning("Frame buffer full, oldest frame will be dropped")
            self._buffer.append(frame)
            self._event.set()
            return True

    async def get(self, timeout: float = 1.0) -> Optional[dict]:
        try:
            await asyncio.wait_for(self._event.wait(), timeout=timeout)
        except asyncio.TimeoutError:
            return None
        async with self._lock:
            if self._buffer:
                frame = self._buffer.popleft()
                if not self._buffer:
                    self._event.clear()
                return frame
        return None

    def size(self) -> int:
        return len(self._buffer)

    def clear(self) -> None:
        self._buffer.clear()
        self._event.clear()
