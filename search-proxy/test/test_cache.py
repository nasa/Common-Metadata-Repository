import fakeredis.aioredis
import pytest

from proxy.cache import ResponseCache

SAMPLE_RESPONSE = {
    "status_code": 200,
    "body": '{"items": []}',
    "headers": {"Content-Type": "application/json"},
}


@pytest.fixture
async def cache():
    client = fakeredis.aioredis.FakeRedis()
    c = ResponseCache(client, max_response_bytes=1_048_576)
    yield c
    await client.aclose()


class TestCacheKey:
    async def test_same_request_same_key(self, cache):
        await cache.set(
            "GET",
            "/search/granules.json",
            "p=1",
            "token",
            SAMPLE_RESPONSE,
            100,
            30,
        )
        result = await cache.get("GET", "/search/granules.json", "p=1", "token")
        assert result == SAMPLE_RESPONSE

    async def test_different_method_misses(self, cache):
        await cache.set(
            "GET",
            "/search/granules.json",
            "p=1",
            "token",
            SAMPLE_RESPONSE,
            100,
            30,
        )
        result = await cache.get("POST", "/search/granules.json", "p=1", "token")
        assert result is None

    async def test_different_path_misses(self, cache):
        await cache.set(
            "GET",
            "/search/granules.json",
            "p=1",
            "token",
            SAMPLE_RESPONSE,
            100,
            30,
        )
        result = await cache.get("GET", "/search/collections.json", "p=1", "token")
        assert result is None

    async def test_different_auth_misses(self, cache):
        await cache.set(
            "GET",
            "/search/granules.json",
            "p=1",
            "token-a",
            SAMPLE_RESPONSE,
            100,
            30,
        )
        result = await cache.get("GET", "/search/granules.json", "p=1", "token-b")
        assert result is None

    async def test_oversized_response_not_cached(self, cache):
        await cache.set(
            "GET",
            "/search/granules.json",
            "p=1",
            "token",
            SAMPLE_RESPONSE,
            2_000_000,
            30,
        )
        result = await cache.get("GET", "/search/granules.json", "p=1", "token")
        assert result is None

    async def test_miss_returns_none(self, cache):
        result = await cache.get("GET", "/search/granules.json", "p=1", "token")
        assert result is None

    async def test_different_search_after_misses(self, cache):
        await cache.set(
            "GET", "/search/granules.json", "p=1", "token",
            SAMPLE_RESPONSE, 100, 30, search_after="cursor-page1",
        )
        result = await cache.get(
            "GET", "/search/granules.json", "p=1", "token", search_after="cursor-page2"
        )
        assert result is None

    async def test_same_search_after_hits(self, cache):
        await cache.set(
            "GET", "/search/granules.json", "p=1", "token",
            SAMPLE_RESPONSE, 100, 30, search_after="cursor-abc",
        )
        result = await cache.get(
            "GET", "/search/granules.json", "p=1", "token", search_after="cursor-abc"
        )
        assert result == SAMPLE_RESPONSE

    async def test_different_accept_misses(self, cache):
        await cache.set(
            "GET", "/search/granules.json", "p=1", "token",
            SAMPLE_RESPONSE, 100, 30, accept="application/json",
        )
        result = await cache.get(
            "GET", "/search/granules.json", "p=1", "token", accept="application/xml"
        )
        assert result is None

    async def test_same_accept_hits(self, cache):
        await cache.set(
            "GET", "/search/granules.json", "p=1", "token",
            SAMPLE_RESPONSE, 100, 30, accept="application/json",
        )
        result = await cache.get(
            "GET", "/search/granules.json", "p=1", "token", accept="application/json"
        )
        assert result == SAMPLE_RESPONSE

    async def test_different_body_hash_misses(self, cache):
        await cache.set(
            "POST", "/search/granules.json", "", "token",
            SAMPLE_RESPONSE, 100, 30, body_hash="aaaa1111",
        )
        result = await cache.get(
            "POST", "/search/granules.json", "", "token", body_hash="bbbb2222"
        )
        assert result is None

    async def test_same_body_hash_hits(self, cache):
        await cache.set(
            "POST", "/search/granules.json", "", "token",
            SAMPLE_RESPONSE, 100, 30, body_hash="aaaa1111",
        )
        result = await cache.get(
            "POST", "/search/granules.json", "", "token", body_hash="aaaa1111"
        )
        assert result == SAMPLE_RESPONSE
