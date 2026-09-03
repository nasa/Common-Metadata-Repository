import gzip
import json
import time
from unittest.mock import AsyncMock

import fakeredis.aioredis
import httpx
import pytest

from proxy.app import DEFAULT_TOGGLES, app, filter_hop_headers
from proxy.cache import ResponseCache
from proxy.config import LaneConfig, LanesConfig, ProxySettings
from proxy.lanes import RequestLanes

BACKEND_JSON = json.dumps({"items": []})
BACKEND_HEADERS = {"content-type": "application/json", "cmr-hits": "5"}


def make_lanes_config(**overrides):
    defaults = {
        "express_permits": 200,
        "standard_permits": 150,
        "heavy_permits": 50,
        "express_cache_ttl": 10,
    }
    defaults.update(overrides)
    return LanesConfig(
        lanes=[
            LaneConfig(
                name="express",
                permits=defaults["express_permits"],
                overflow="standard",
                cache_ttl=defaults["express_cache_ttl"],
                default=True,
            ),
            LaneConfig(
                name="standard",
                permits=defaults["standard_permits"],
                cache_ttl=15,
            ),
            LaneConfig(
                name="heavy",
                permits=defaults["heavy_permits"],
                cache_ttl=30,
                retry_after=10,
            ),
        ]
    )


def make_backend_response(
    status_code=200,
    body=BACKEND_JSON,
    headers=None,
):
    """Create a mock httpx.Response."""
    headers = headers or BACKEND_HEADERS
    return httpx.Response(
        status_code=status_code,
        content=body.encode(),
        headers=headers,
        request=httpx.Request("GET", "http://backend/test"),
    )


@pytest.fixture
async def client():
    """Test client with fakeredis and mocked backend."""
    fake_redis = fakeredis.aioredis.FakeRedis()
    config = make_lanes_config()

    settings = ProxySettings()
    app.state.settings = settings
    app.state.redis = fake_redis
    app.state.lanes_config = config
    app.state.lanes = RequestLanes(config, fake_redis)
    app.state.cache = ResponseCache(fake_redis, settings.max_cache_response_bytes)
    app.state.toggles = dict(DEFAULT_TOGGLES)
    app.state.backend = AsyncMock(spec=httpx.AsyncClient)
    app.state.backend.get = AsyncMock(return_value=make_backend_response())
    app.state.backend.request = AsyncMock(return_value=make_backend_response())

    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://test",
    ) as c:
        yield c

    await fake_redis.aclose()


# Routing


class TestRouting:
    async def test_search_request_is_proxied(self, client):
        resp = await client.get("/search/granules.json?provider=POCLOUD")
        assert resp.status_code == 200
        assert resp.json() == {"items": []}

    async def test_health_returns_cmr_format(self, client):
        """Health check matches CMR's ok?/dependencies format."""
        app.state.backend.get.return_value = make_backend_response()
        resp = await client.get("/health")
        assert resp.status_code == 200
        data = resp.json()
        assert data["ok?"] is True
        assert "dependencies" in data
        assert data["dependencies"]["redis"]["ok?"] is True
        assert data["dependencies"]["search"]["ok?"] is True

    async def test_health_includes_lane_status(self, client):
        """Health check includes per-lane utilization."""
        app.state.backend.get.return_value = make_backend_response()
        resp = await client.get("/health")
        data = resp.json()
        deps = data["dependencies"]
        assert "lane-express" in deps
        assert "lane-standard" in deps
        assert "lane-heavy" in deps
        assert deps["lane-heavy"]["permits"] == 50
        assert deps["lane-heavy"]["active"] == 0
        assert deps["lane-heavy"]["ok?"] is True

    async def test_health_reports_lane_at_capacity(self, client):
        """Health reports at_capacity but remains ok? true — informational only."""
        app.state.backend.get.return_value = make_backend_response()
        fake_redis = app.state.redis
        future = time.time() + 300
        await fake_redis.zadd("lane:heavy:active", {f"req-{i}": future for i in range(50)})
        resp = await client.get("/health")
        data = resp.json()
        assert resp.status_code == 200
        assert data["ok?"] is True
        assert data["dependencies"]["lane-heavy"]["ok?"] is True
        assert data["dependencies"]["lane-heavy"]["at_capacity"] is True
        await fake_redis.delete("lane:heavy:active")

    async def test_health_shallow_always_200(self, client):
        resp = await client.get("/health/shallow")
        assert resp.status_code == 200
        assert resp.json()["ok?"] is True

    async def test_health_not_cached(self, client):
        """/health is not cached — every call re-checks the backend."""
        app.state.backend.get.return_value = make_backend_response()
        await client.get("/health")
        await client.get("/health")
        health_calls = [
            c
            for c in app.state.backend.get.call_args_list
            if "/search/health" in str(c)
        ]
        assert len(health_calls) == 2


# Response cache


class TestResponseCache:
    async def test_second_identical_request_hits_cache(self, client):
        """Polling the same query should hit cache on repeat."""
        await client.get("/search/granules.json?provider=POCLOUD")
        await client.get("/search/granules.json?provider=POCLOUD")
        assert app.state.backend.get.call_count == 1

    async def test_different_params_miss_cache(self, client):
        await client.get("/search/granules.json?provider=POCLOUD")
        await client.get("/search/granules.json?provider=LPDAAC")
        assert app.state.backend.get.call_count == 2

    async def test_different_auth_tokens_miss_cache(self, client):
        await client.get(
            "/search/granules.json?provider=POCLOUD",
            headers={"Echo-Token": "user-a"},
        )
        await client.get(
            "/search/granules.json?provider=POCLOUD",
            headers={"Echo-Token": "user-b"},
        )
        assert app.state.backend.get.call_count == 2

    async def test_error_responses_not_cached(self, client):
        app.state.backend.get.return_value = make_backend_response(
            status_code=500, body='{"errors": ["oops"]}'
        )
        await client.get("/search/granules.json?provider=POCLOUD")
        app.state.backend.get.return_value = make_backend_response()
        resp = await client.get("/search/granules.json?provider=POCLOUD")
        assert resp.status_code == 200
        assert app.state.backend.get.call_count == 2

    async def test_no_cache_when_ttl_is_zero(self, client):
        """Lanes with cache_ttl=0 should not cache."""
        app.state.lanes_config = make_lanes_config(express_cache_ttl=0)

        await client.get("/search/granules.json?concept_id=C123")
        await client.get("/search/granules.json?concept_id=C123")
        assert app.state.backend.get.call_count == 2


# Classification integration


class TestClassificationIntegration:
    async def test_express_request(self, client):
        resp = await client.get("/search/collections.json?concept_id=C123")
        assert resp.status_code == 200

    async def test_heavy_request(self, client):
        resp = await client.get("/search/granules.json?include_facets=v2")
        assert resp.status_code == 200


# Load shedding


class TestLoadShedding:
    async def test_429_on_load_shedding(self, client):
        fake_redis = app.state.redis
        config = make_lanes_config(
            express_permits=1,
            standard_permits=1,
            heavy_permits=1,
        )
        app.state.lanes = RequestLanes(config, fake_redis)

        # Fill the heavy lane via Redis
        await fake_redis.zadd("lane:heavy:active", {"blocking-req": time.time() + 300})
        resp = await client.get("/search/granules.json?include_facets=v2")
        assert resp.status_code == 429
        assert "Retry-After" in resp.headers
        assert "overloaded" in resp.json()["errors"][0]

        await fake_redis.delete("lane:heavy:active")


# Backend errors


class TestBackendErrors:
    async def test_backend_timeout_returns_504(self, client):
        app.state.backend.get.side_effect = httpx.TimeoutException("timed out")
        resp = await client.get("/search/granules.json?provider=POCLOUD")
        assert resp.status_code == 504
        assert "timed out" in resp.json()["errors"][0].lower()
        assert "cmr-request-id" in resp.headers

    async def test_backend_connect_error_returns_502(self, client):
        app.state.backend.get.side_effect = httpx.ConnectError("refused")
        resp = await client.get("/search/granules.json?provider=POCLOUD")
        assert resp.status_code == 502
        assert "unavailable" in resp.json()["errors"][0].lower()

    async def test_malformed_content_length_does_not_crash(self, client):
        app.state.backend.request.return_value = make_backend_response()
        resp = await client.post(
            "/search/granules.json",
            content="provider=POCLOUD",
            headers={
                "content-type": "application/x-www-form-urlencoded",
                "content-length": "not-a-number",
            },
        )
        assert resp.status_code == 200


# Forwarding


class TestForwarding:
    async def test_preserves_backend_status_code(self, client):
        app.state.backend.get.return_value = make_backend_response(status_code=404)
        resp = await client.get("/search/granules.json?concept_id=MISSING")
        assert resp.status_code == 404

    async def test_post_request_forwarded(self, client):
        app.state.backend.request.return_value = make_backend_response()
        resp = await client.post(
            "/search/granules.json",
            content="provider=POCLOUD",
            headers={"content-type": "application/x-www-form-urlencoded"},
        )
        assert resp.status_code == 200
        app.state.backend.request.assert_called_once()

    async def test_request_id_injected_to_backend(self, client):
        await client.get(
            "/search/granules.json?provider=X",
            headers={"cmr-request-id": "trace-123"},
        )
        call_args = app.state.backend.get.call_args
        forwarded_headers = call_args.kwargs.get(
            "headers", call_args[1].get("headers", {})
        )
        assert forwarded_headers["cmr-request-id"] == "trace-123"

    async def test_generated_request_id_in_response(self, client):
        resp = await client.get("/search/granules.json?provider=X")
        assert resp.headers.get("cmr-request-id")

    async def test_query_string_not_double_encoded(self, client):
        await client.get("/search/granules.json?polygon=1,2,3,4&provider=POCLOUD")
        call_args = app.state.backend.get.call_args
        forwarded_url = (
            str(call_args.args[0])
            if call_args.args
            else str(call_args.kwargs.get("url", ""))
        )
        assert "polygon=1,2,3,4" in forwarded_url
        assert "provider=POCLOUD" in forwarded_url
        assert "%3D" not in forwarded_url
        assert "%26" not in forwarded_url

    async def test_hop_headers_filtered(self, client):
        app.state.backend.get.return_value = make_backend_response(
            headers={
                "content-type": "application/json",
                "transfer-encoding": "chunked",
                "connection": "keep-alive",
                "cmr-hits": "10",
            }
        )
        resp = await client.get("/search/granules.json?provider=X")
        assert "transfer-encoding" not in resp.headers
        assert "connection" not in resp.headers

    async def test_content_encoding_stripped(self, client):
        compressed = gzip.compress(BACKEND_JSON.encode())
        app.state.backend.get.return_value = httpx.Response(
            status_code=200,
            content=compressed,
            headers={"content-type": "application/json", "content-encoding": "gzip", "cmr-hits": "5"},
            request=httpx.Request("GET", "http://backend/test"),
        )
        resp = await client.get("/search/granules.json?provider=X")
        assert "content-encoding" not in resp.headers

    async def test_content_length_not_forwarded_from_backend(self, client):
        # Backend claims 746 bytes (e.g. compressed size) but actual body is smaller.
        # Proxy strips the backend content-length; framework sets the correct value.
        app.state.backend.get.return_value = make_backend_response(
            headers={"content-type": "application/json", "content-length": "746", "cmr-hits": "5"}
        )
        resp = await client.get("/search/granules.json?provider=X")
        assert resp.headers.get("content-length") != "746"

    async def test_proxy_marker_header_injected(self, client):
        await client.get("/search/granules.json?provider=X")
        call_args = app.state.backend.get.call_args
        forwarded_headers = call_args.kwargs.get(
            "headers", call_args[1].get("headers", {})
        )
        assert forwarded_headers.get("x-cmr-proxy-request") == "1"


# Hop header filtering


class TestFilterHopHeaders:
    def test_removes_hop_headers(self):
        headers = httpx.Headers(
            {
                "content-type": "application/json",
                "transfer-encoding": "chunked",
                "connection": "keep-alive",
            }
        )
        filtered = filter_hop_headers(headers)
        assert "content-type" in filtered
        assert "transfer-encoding" not in filtered
        assert "connection" not in filtered

    def test_preserves_cmr_headers(self):
        headers = httpx.Headers(
            {
                "cmr-hits": "100",
                "cmr-took": "45",
                "content-type": "application/json",
            }
        )
        filtered = filter_hop_headers(headers)
        assert filtered["cmr-hits"] == "100"
        assert filtered["cmr-took"] == "45"


# Request ID


class TestRequestId:
    async def test_request_id_propagated(self, client):
        resp = await client.get(
            "/search/granules.json?provider=X",
            headers={"cmr-request-id": "my-req-123"},
        )
        assert resp.headers.get("cmr-request-id") == "my-req-123"


# POST body classification


class TestPostBodyClassification:
    async def test_post_json_body_not_parsed(self, client):
        app.state.backend.request.return_value = make_backend_response()
        resp = await client.post(
            "/search/granules.json",
            content='{"provider": "POCLOUD"}',
            headers={"content-type": "application/json"},
        )
        assert resp.status_code == 200

    async def test_oversized_content_length_returns_413(self, client):
        app.state.settings.max_request_body_bytes = 100
        resp = await client.post(
            "/search/granules.json",
            content="x" * 50,
            headers={
                "content-type": "application/x-www-form-urlencoded",
                "content-length": "999999",
            },
        )
        assert resp.status_code == 413
        assert "too large" in resp.json()["errors"][0]

    async def test_oversized_body_returns_413(self, client):
        app.state.settings.max_request_body_bytes = 100
        resp = await client.post(
            "/search/granules.json",
            content="x" * 200,
            headers={"content-type": "application/x-www-form-urlencoded"},
        )
        assert resp.status_code == 413

    async def test_non_utf8_post_body_returns_200(self, client):
        app.state.backend.request.return_value = make_backend_response()
        resp = await client.post(
            "/search/granules.json",
            content=b"\xff\xfe\x00\x01",
            headers={"content-type": "application/x-www-form-urlencoded"},
        )
        assert resp.status_code == 200

    async def test_non_utf8_post_body_still_forwarded(self, client):
        app.state.backend.request.return_value = make_backend_response()
        await client.post(
            "/search/granules.json",
            content=b"\xff\xfe\x00\x01",
            headers={"content-type": "application/x-www-form-urlencoded"},
        )
        app.state.backend.request.assert_called_once()


# Feature toggles


class TestBypassToggle:
    async def test_bypass_forwards_directly(self, client):
        app.state.toggles["bypass_enabled"] = True
        try:
            resp = await client.get("/search/granules.json?provider=POCLOUD")
            assert resp.status_code == 200
            app.state.backend.get.assert_called_once()
        finally:
            app.state.toggles["bypass_enabled"] = False

    async def test_bypass_timeout_returns_504(self, client):
        app.state.toggles["bypass_enabled"] = True
        app.state.backend.get.side_effect = httpx.TimeoutException("timed out")
        try:
            resp = await client.get("/search/granules.json?provider=POCLOUD")
            assert resp.status_code == 504
        finally:
            app.state.backend.get.side_effect = None
            app.state.toggles["bypass_enabled"] = False

    async def test_bypass_connect_error_returns_502(self, client):
        app.state.toggles["bypass_enabled"] = True
        app.state.backend.get.side_effect = httpx.ConnectError("refused")
        try:
            resp = await client.get("/search/granules.json?provider=POCLOUD")
            assert resp.status_code == 502
        finally:
            app.state.backend.get.side_effect = None
            app.state.toggles["bypass_enabled"] = False


class TestCacheToggle:
    async def test_cache_disabled_skips_caching(self, client):
        app.state.toggles["cache_enabled"] = False
        try:
            await client.get("/search/granules.json?provider=POCLOUD")
            await client.get("/search/granules.json?provider=POCLOUD")
            assert app.state.backend.get.call_count == 2
        finally:
            app.state.toggles["cache_enabled"] = True


class TestLoadSheddingToggle:
    async def test_load_shedding_disabled_allows_over_capacity(self, client):
        app.state.toggles["load_shedding_enabled"] = False
        fake_redis = app.state.redis
        config = make_lanes_config(heavy_permits=1)
        app.state.lanes = RequestLanes(config, fake_redis)
        await fake_redis.zadd("lane:heavy:active", {"blocking-req": time.time() + 300})
        try:
            resp = await client.get("/search/granules.json?include_facets=v2")
            assert resp.status_code == 200
        finally:
            await fake_redis.delete("lane:heavy:active")
            app.state.toggles["load_shedding_enabled"] = True
            app.state.lanes = RequestLanes(make_lanes_config(), fake_redis)


class TestClassificationToggle:
    async def test_classification_disabled_uses_default_lane(self, client):
        """With classification off, a normally-heavy request routes to express."""
        app.state.toggles["classification_enabled"] = False
        fake_redis = app.state.redis
        await fake_redis.set("lane:heavy:active", 50)
        try:
            resp = await client.get("/search/granules.json?include_facets=v2")
            assert resp.status_code == 200
        finally:
            await fake_redis.delete("lane:heavy:active")
            app.state.toggles["classification_enabled"] = True


# Cache key segmentation


class TestCacheKeySegmentation:
    async def test_search_after_header_creates_separate_cache_entry(self, client):
        await client.get("/search/granules.json?provider=POCLOUD")
        await client.get(
            "/search/granules.json?provider=POCLOUD",
            headers={"cmr-search-after": "cursor-xyz"},
        )
        assert app.state.backend.get.call_count == 2

    async def test_same_search_after_hits_cache(self, client):
        await client.get(
            "/search/granules.json?provider=POCLOUD",
            headers={"cmr-search-after": "cursor-xyz"},
        )
        await client.get(
            "/search/granules.json?provider=POCLOUD",
            headers={"cmr-search-after": "cursor-xyz"},
        )
        assert app.state.backend.get.call_count == 1

    async def test_different_accept_creates_separate_cache_entry(self, client):
        await client.get(
            "/search/granules.json?provider=POCLOUD",
            headers={"accept": "application/json"},
        )
        await client.get(
            "/search/granules.json?provider=POCLOUD",
            headers={"accept": "application/xml"},
        )
        assert app.state.backend.get.call_count == 2

    async def test_post_different_bodies_create_separate_cache_entries(self, client):
        app.state.backend.request.return_value = make_backend_response()
        await client.post(
            "/search/granules.json",
            content="provider=POCLOUD",
            headers={"content-type": "application/x-www-form-urlencoded"},
        )
        await client.post(
            "/search/granules.json",
            content="provider=LPDAAC",
            headers={"content-type": "application/x-www-form-urlencoded"},
        )
        assert app.state.backend.request.call_count == 2

    async def test_post_same_body_hits_cache(self, client):
        app.state.backend.request.return_value = make_backend_response()
        await client.post(
            "/search/granules.json",
            content="provider=POCLOUD",
            headers={"content-type": "application/x-www-form-urlencoded"},
        )
        await client.post(
            "/search/granules.json",
            content="provider=POCLOUD",
            headers={"content-type": "application/x-www-form-urlencoded"},
        )
        assert app.state.backend.request.call_count == 1

    async def test_same_accept_hits_cache(self, client):
        await client.get(
            "/search/granules.json?provider=POCLOUD",
            headers={"accept": "application/json"},
        )
        await client.get(
            "/search/granules.json?provider=POCLOUD",
            headers={"accept": "application/json"},
        )
        assert app.state.backend.get.call_count == 1
