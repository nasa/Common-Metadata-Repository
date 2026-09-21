import logging
import uuid
from contextlib import asynccontextmanager

import redis.asyncio

from proxy.config import LanesConfig

logger = logging.getLogger(__name__)


class LoadSheddingError(Exception):
    def __init__(self, lane_name: str, retry_after: int):
        self.lane_name = lane_name
        self.retry_after = retry_after


class RequestLanes:
    """Redis-backed distributed traffic lanes.

    Each lane's active permits are tracked in a Redis sorted set keyed
    lane:{name}:active. Each in-flight request occupies one member (its UUID);
    the score is the expiry epoch from Redis server time. Entries whose score
    has passed are pruned on the next acquire, so leaked permits from crashed
    tasks recover automatically without manual intervention.

    When Redis is unavailable, acquire fails open: requests are forwarded
    without a permit rather than 429'd. This degrades gracefully under
    infrastructure failure at the cost of temporary over-capacity."""

    def __init__(
        self,
        config: LanesConfig,
        redis_client: redis.asyncio.Redis,
        permit_ttl: int = 300,
    ):
        self.config = config
        self.redis = redis_client
        self.permit_ttl = permit_ttl

    def _lane_key(self, lane_name: str) -> str:
        return f"lane:{lane_name}:active"

    async def _redis_now(self) -> float:
        """Return current time from the Redis server to avoid ECS task clock skew."""
        seconds, microseconds = await self.redis.time()
        return seconds + microseconds / 1_000_000

    async def _try_acquire(self, lane_name: str, permits: int, request_id: str, redis_now: float) -> bool:
        """Prune expired entries, count active, and add this request if under limit.

        Redis exceptions propagate to the caller, which decides fail-open vs fail-closed."""
        key = self._lane_key(lane_name)
        await self.redis.zremrangebyscore(key, "-inf", redis_now)
        count = await self.redis.zcard(key)
        if count >= permits:
            return False
        await self.redis.zadd(key, {request_id: redis_now + self.permit_ttl})
        return True

    async def _release(self, lane_name: str, request_id: str) -> None:
        """Remove this request's permit entry from the sorted set."""
        key = self._lane_key(lane_name)
        try:
            await self.redis.zrem(key, request_id)
        except Exception:
            # Log and swallow so a Redis failure here doesn't propagate out of
            # the finally block and crash the ASGI handler. The permit leaks
            # but will expire naturally via the TTL score.
            logger.error(
                "permit_release_failed",
                extra={"lane": lane_name},
                exc_info=True,
            )

    async def _acquire_permit(
        self, lane_name: str, load_shedding_enabled: bool, request_id: str
    ) -> tuple[str, bool]:
        """Try to acquire a permit, returning (lane_name, permit_stored).

        permit_stored is False when Redis is unavailable and the request is
        allowed through without a permit (fail-open). The caller must not
        attempt a release in that case."""
        lane = self.config.get(lane_name)

        try:
            redis_now = await self._redis_now()

            if await self._try_acquire(lane.name, lane.permits, request_id, redis_now):
                return lane.name, True

            if lane.overflow:
                overflow_lane = self.config.get(lane.overflow)
                if await self._try_acquire(overflow_lane.name, overflow_lane.permits, request_id, redis_now):
                    return overflow_lane.name, True

            if not load_shedding_enabled:
                await self.redis.zadd(self._lane_key(lane.name), {request_id: redis_now + self.permit_ttl})
                logger.warning(
                    "load_shed_suppressed",
                    extra={"lane": lane.name, "load_shedding_enabled": False},
                )
                return lane.name, True

            raise LoadSheddingError(lane.name, lane.retry_after)

        except LoadSheddingError:
            raise
        except Exception:
            logger.error(
                "permit_acquire_failed",
                extra={"lane": lane.name},
                exc_info=True,
            )
            logger.warning(
                "permit_bypassed",
                extra={"lane": lane.name, "reason": "redis_unavailable"},
            )
            return lane.name, False

    @asynccontextmanager
    async def acquire(self, lane_name: str, load_shedding_enabled: bool = True):
        """Acquire a distributed permit for the named lane.

        Yields the name of the lane that was actually acquired (may differ
        from the requested lane if overflow occurred). The permit is always
        released when the context exits, even on exception. If Redis was
        unavailable during acquire (fail-open), no release is attempted."""
        request_id = str(uuid.uuid4())
        actual_name, permit_stored = await self._acquire_permit(lane_name, load_shedding_enabled, request_id)
        try:
            yield actual_name
        finally:
            if permit_stored:
                await self._release(actual_name, request_id)
