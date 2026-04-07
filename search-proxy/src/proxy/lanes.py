from contextlib import asynccontextmanager

import redis.asyncio

from proxy.config import LanesConfig


class LoadSheddingError(Exception):
    def __init__(self, lane_name: str, retry_after: int):
        self.lane_name = lane_name
        self.retry_after = retry_after


class RequestLanes:
    """Redis-backed distributed traffic lanes.

    Permits are tracked as atomic counters in Redis, shared across all
    proxy instances. Each lane key (lane:{name}:active) holds the current
    number of in-flight requests for that lane."""

    def __init__(self, config: LanesConfig, redis_client: redis.asyncio.Redis):
        self.config = config
        self.redis = redis_client

    def _lane_key(self, lane_name: str) -> str:
        return f"lane:{lane_name}:active"

    async def _try_acquire(self, lane_name: str, permits: int) -> bool:
        """Atomically increment the lane counter and check against limit.

        Uses INCR for atomicity — if the post-increment value exceeds
        the permit limit, immediately DECR to roll back."""
        key = self._lane_key(lane_name)
        current = await self.redis.incr(key)
        if current > permits:
            await self.redis.decr(key)
            return False
        return True

    async def _release(self, lane_name: str) -> None:
        """Decrement the lane counter. Floors at zero to prevent
        negative counts from orphaned releases."""
        key = self._lane_key(lane_name)
        result = await self.redis.decr(key)
        if result < 0:
            await self.redis.set(key, 0)

    async def _acquire_permit(self, lane_name: str) -> str:
        """Try to acquire a permit, returning the lane name on success.

        Tries the requested lane first. If full and an overflow lane is
        configured, tries that. Otherwise sheds immediately."""
        lane = self.config.get(lane_name)

        if await self._try_acquire(lane.name, lane.permits):
            return lane.name

        # Primary lane full — try overflow if configured
        if lane.overflow:
            overflow_lane = self.config.get(lane.overflow)
            if await self._try_acquire(overflow_lane.name, overflow_lane.permits):
                return overflow_lane.name

        raise LoadSheddingError(lane.name, lane.retry_after)

    @asynccontextmanager
    async def acquire(self, lane_name: str):
        """Acquire a distributed permit for the named lane.

        Yields the name of the lane that was actually acquired (may differ
        from the requested lane if overflow occurred). The permit is always
        released when the context exits, even on exception."""
        actual_name = await self._acquire_permit(lane_name)
        try:
            yield actual_name
        finally:
            await self._release(actual_name)
