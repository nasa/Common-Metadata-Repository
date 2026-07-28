import time
from unittest.mock import AsyncMock, patch

import fakeredis.aioredis
import pytest
from redis.exceptions import MaxConnectionsError

from proxy.config import LaneConfig, LanesConfig
from proxy.lanes import LoadSheddingError, RequestLanes


def make_config(**overrides):
    """Build a LanesConfig with sensible defaults, overridable per lane."""
    defaults = {
        "express_permits": 200,
        "standard_permits": 150,
        "heavy_permits": 50,
    }
    defaults.update(overrides)
    return LanesConfig(
        lanes=[
            LaneConfig(
                name="express",
                permits=defaults["express_permits"],
                overflow="standard",
                default=True,
            ),
            LaneConfig(
                name="standard",
                permits=defaults["standard_permits"],
            ),
            LaneConfig(
                name="heavy",
                permits=defaults["heavy_permits"],
            ),
        ]
    )


@pytest.fixture
async def redis_client():
    client = fakeredis.aioredis.FakeRedis()
    yield client
    await client.aclose()


@pytest.fixture
async def lanes(redis_client):
    return RequestLanes(make_config(), redis_client)


@pytest.fixture
async def tight_lanes(redis_client):
    """Lanes with 1 permit each for testing contention."""
    return RequestLanes(
        make_config(
            express_permits=1,
            standard_permits=1,
            heavy_permits=1,
        ),
        redis_client,
    )


class TestBasicAcquisition:
    async def test_express_acquires_and_releases(self, lanes):
        async with lanes.acquire("express") as actual:
            assert actual == "express"

    async def test_standard_acquires_and_releases(self, lanes):
        async with lanes.acquire("standard") as actual:
            assert actual == "standard"

    async def test_heavy_acquires_and_releases(self, lanes):
        async with lanes.acquire("heavy") as actual:
            assert actual == "heavy"


class TestPermitRelease:
    async def test_permit_released_after_normal_exit(self, tight_lanes):
        async with tight_lanes.acquire("heavy"):
            pass
        async with tight_lanes.acquire("heavy") as actual:
            assert actual == "heavy"

    async def test_permit_released_after_exception(self, tight_lanes):
        with pytest.raises(ValueError):
            async with tight_lanes.acquire("heavy"):
                raise ValueError("boom")
        async with tight_lanes.acquire("heavy") as actual:
            assert actual == "heavy"


class TestDistributedCounting:
    async def test_permits_shared_across_instances(self, redis_client):
        """Two RequestLanes sharing the same Redis enforce a global limit."""
        config = make_config(heavy_permits=2)
        lanes_a = RequestLanes(config, redis_client)
        lanes_b = RequestLanes(config, redis_client)

        async with lanes_a.acquire("heavy") as a:
            async with lanes_b.acquire("heavy") as b:
                assert a == "heavy"
                assert b == "heavy"

                with pytest.raises(LoadSheddingError):
                    async with lanes_a.acquire("heavy"):
                        pass

    async def test_active_count_is_zero_after_all_releases(self, redis_client):
        """After all permits are released, the sorted set should be empty."""
        config = make_config(heavy_permits=5)
        lanes = RequestLanes(config, redis_client)

        for _ in range(5):
            async with lanes.acquire("heavy"):
                pass

        count = await redis_client.zcard("lane:heavy:active")
        assert count == 0


class TestExpressOverflow:
    async def test_express_overflows_to_standard(self, redis_client):
        """When express is full, express requests overflow to standard."""
        config = make_config(express_permits=1, standard_permits=1)
        lanes = RequestLanes(config, redis_client)

        async with lanes.acquire("express") as first:
            assert first == "express"
            async with lanes.acquire("express") as second:
                assert second == "standard"

    async def test_express_sheds_when_both_full(self, redis_client):
        """When both express and standard are full, express sheds."""
        config = make_config(express_permits=1, standard_permits=1)
        lanes = RequestLanes(config, redis_client)

        async with lanes.acquire("express"):
            async with lanes.acquire("standard"):
                with pytest.raises(LoadSheddingError) as exc_info:
                    async with lanes.acquire("express"):
                        pass
                assert exc_info.value.lane_name == "express"


class TestLoadShedding:
    async def test_sheds_when_full(self, tight_lanes):
        async with tight_lanes.acquire("heavy"):
            with pytest.raises(LoadSheddingError) as exc_info:
                async with tight_lanes.acquire("heavy"):
                    pass
            assert exc_info.value.lane_name == "heavy"

    async def test_load_shedding_error_has_retry_after(self, tight_lanes):
        async with tight_lanes.acquire("heavy"):
            with pytest.raises(LoadSheddingError) as exc_info:
                async with tight_lanes.acquire("heavy"):
                    pass
            assert exc_info.value.retry_after == 5


class TestLaneIsolation:
    async def test_tiers_are_independent(self, tight_lanes):
        """Filling one tier doesn't affect others."""
        async with tight_lanes.acquire("heavy"):
            async with tight_lanes.acquire("standard") as actual:
                assert actual == "standard"

    async def test_concurrent_acquisition(self, lanes):
        """Multiple tiers can be held simultaneously."""
        async with lanes.acquire("express") as t1:
            async with lanes.acquire("standard") as t2:
                async with lanes.acquire("heavy") as t3:
                    assert t1 == "express"
                    assert t2 == "standard"
                    assert t3 == "heavy"


class TestUnknownLaneFallback:
    async def test_unknown_lane_falls_back_to_default(self, lanes):
        async with lanes.acquire("nonexistent") as actual:
            assert actual == "express"


class TestRedisFailOpen:
    async def test_acquire_succeeds_when_redis_unavailable(self, lanes):
        """When Redis is down, acquire fails open rather than 429ing the request."""
        with patch.object(lanes.redis, "time", new=AsyncMock(side_effect=Exception("Redis down"))):
            async with lanes.acquire("heavy") as actual:
                assert actual == "heavy"

    async def test_no_release_attempted_on_fail_open(self, lanes):
        """When Redis fails during acquire, no release is attempted (nothing was stored)."""
        with patch.object(lanes.redis, "time", new=AsyncMock(side_effect=Exception("Redis down"))):
            with patch.object(lanes.redis, "zrem", new=AsyncMock()) as mock_zrem:
                async with lanes.acquire("heavy"):
                    pass
                mock_zrem.assert_not_called()

    async def test_fail_open_does_not_propagate_exception(self, lanes):
        """A Redis error during acquire must not surface to the caller as an exception."""
        with patch.object(lanes.redis, "time", new=AsyncMock(side_effect=Exception("Redis down"))):
            try:
                async with lanes.acquire("heavy"):
                    pass
            except Exception:
                pytest.fail("Redis exception escaped from acquire")


class TestReleaseFailure:
    async def test_release_redis_error_does_not_propagate(self, lanes):
        """MaxConnectionsError in _release must not escape to the caller."""
        with patch.object(lanes.redis, "zrem", new=AsyncMock(side_effect=MaxConnectionsError("Too many connections"))):
            try:
                async with lanes.acquire("heavy"):
                    pass
            except MaxConnectionsError:
                pytest.fail("MaxConnectionsError escaped from _release")

    async def test_release_redis_error_is_logged(self, lanes, caplog):
        import logging
        with patch.object(lanes.redis, "zrem", new=AsyncMock(side_effect=MaxConnectionsError("Too many connections"))):
            with caplog.at_level(logging.ERROR, logger="proxy.lanes"):
                async with lanes.acquire("heavy"):
                    pass
        assert "permit_release_failed" in caplog.text


class TestLoadSheddingDisabled:
    async def test_force_acquires_when_lane_full(self, tight_lanes):
        """With load shedding off, over-capacity requests are not rejected."""
        async with tight_lanes.acquire("heavy"):
            async with tight_lanes.acquire("heavy", load_shedding_enabled=False) as actual:
                assert actual == "heavy"

    async def test_does_not_raise_load_shedding_error(self, tight_lanes):
        async with tight_lanes.acquire("heavy"):
            try:
                async with tight_lanes.acquire("heavy", load_shedding_enabled=False):
                    pass
            except LoadSheddingError:
                pytest.fail("LoadSheddingError raised with load_shedding_enabled=False")


class TestTTLExpiry:
    async def test_expired_permit_is_pruned_on_next_acquire(self, redis_client):
        """A leaked permit with an expired score is cleaned up on the next acquire."""
        config = make_config(heavy_permits=1)
        lanes = RequestLanes(config, redis_client)

        # Simulate a leaked permit: score is in the past so it is already expired
        expired_score = time.time() - 10
        await redis_client.zadd("lane:heavy:active", {"leaked-request-id": expired_score})

        # The expired entry should be pruned and the slot freed for a new request
        async with lanes.acquire("heavy") as actual:
            assert actual == "heavy"

    async def test_non_expired_permit_blocks_acquire(self, redis_client):
        """A permit with a future score is still counted as active."""
        config = make_config(heavy_permits=1)
        lanes = RequestLanes(config, redis_client)

        future_score = time.time() + 300
        await redis_client.zadd("lane:heavy:active", {"active-request-id": future_score})

        with pytest.raises(LoadSheddingError):
            async with lanes.acquire("heavy"):
                pass

    async def test_multiple_expired_permits_all_pruned(self, redis_client):
        """Multiple leaked permits are all removed before counting capacity."""
        config = make_config(heavy_permits=2)
        lanes = RequestLanes(config, redis_client)

        expired = time.time() - 10
        await redis_client.zadd("lane:heavy:active", {
            "leaked-1": expired,
            "leaked-2": expired,
            "leaked-3": expired,
        })

        # All three expired entries pruned — both permits now free
        async with lanes.acquire("heavy"):
            async with lanes.acquire("heavy") as actual:
                assert actual == "heavy"


class TestUniquePermitSlots:
    async def test_identical_concurrent_requests_each_occupy_a_slot(self, redis_client):
        """Two concurrent requests occupy two distinct slots in the sorted set."""
        config = make_config(express_permits=2)
        lanes = RequestLanes(config, redis_client)

        async with lanes.acquire("express"):
            async with lanes.acquire("express"):
                count = await redis_client.zcard("lane:express:active")
                assert count == 2

    async def test_third_request_shed_when_two_permit_lane_full(self, redis_client):
        """A third request is shed when a 2-permit no-overflow lane is fully occupied."""
        config = make_config(heavy_permits=2)
        lanes = RequestLanes(config, redis_client)

        async with lanes.acquire("heavy"):
            async with lanes.acquire("heavy"):
                with pytest.raises(LoadSheddingError):
                    async with lanes.acquire("heavy"):
                        pass

    async def test_permit_slot_removed_on_release(self, redis_client):
        """Each release removes exactly one entry from the sorted set."""
        config = make_config(heavy_permits=3)
        lanes = RequestLanes(config, redis_client)

        async with lanes.acquire("heavy"):
            async with lanes.acquire("heavy"):
                count_during = await redis_client.zcard("lane:heavy:active")
                assert count_during == 2
            count_after_one_release = await redis_client.zcard("lane:heavy:active")
            assert count_after_one_release == 1
