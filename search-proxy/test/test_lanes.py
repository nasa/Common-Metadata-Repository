import fakeredis.aioredis
import pytest

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

                # Third attempt from either instance should be shed
                with pytest.raises(LoadSheddingError):
                    async with lanes_a.acquire("heavy"):
                        pass

    async def test_redis_counter_returns_to_zero(self, redis_client):
        """After all permits are released, the counter should be zero."""
        config = make_config(heavy_permits=5)
        lanes = RequestLanes(config, redis_client)

        for _ in range(5):
            async with lanes.acquire("heavy"):
                pass

        counter = await redis_client.get("lane:heavy:active")
        assert int(counter) == 0


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
