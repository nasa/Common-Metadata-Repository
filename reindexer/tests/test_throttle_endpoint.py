"""
Unit tests for GET /throttle and PUT /throttle.

The throttler singleton is replaced by a MagicMock to avoid touching real
rate state or starting background threads.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_throttle_endpoint.py -v
"""
from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def client():
    from app.auth import require_auth
    from app.main import app
    app.dependency_overrides[require_auth] = lambda: "test-token"
    yield TestClient(app, raise_server_exceptions=False)
    app.dependency_overrides.clear()


@pytest.fixture(autouse=True)
def mock_throttler(monkeypatch):
    m = MagicMock()
    m.get_rate.return_value = 600.0
    monkeypatch.setattr("app.routers.throttle.throttler", m)
    return m


# ---------------------------------------------------------------------------
# GET /throttle
# ---------------------------------------------------------------------------

class TestGetThrottle:

    def test_returns_200(self, client):
        r = client.get("/reindexer/throttle")
        assert r.status_code == 200

    def test_returns_current_rate(self, client):
        import app.routers.throttle as _t
        _t.throttler.get_rate.return_value = 1200.0
        r = client.get("/reindexer/throttle")
        assert r.json()["rate_per_minute"] == pytest.approx(1200.0)

    def test_requires_no_auth(self, client):
        # GET /throttle is open; even without the dependency override it must return 200
        r = client.get("/reindexer/throttle")
        assert r.status_code == 200


# ---------------------------------------------------------------------------
# PUT /throttle
# ---------------------------------------------------------------------------

class TestPutThrottle:

    def test_returns_200(self, client):
        r = client.put("/reindexer/throttle", json={"rate_per_minute": 300})
        assert r.status_code == 200

    def test_calls_set_rate_with_new_value(self, client):
        import app.routers.throttle as _t
        client.put("/reindexer/throttle", json={"rate_per_minute": 450})
        _t.throttler.set_rate.assert_called_once_with(450)

    def test_response_body_contains_new_rate(self, client):
        r = client.put("/reindexer/throttle", json={"rate_per_minute": 900})
        assert r.json()["rate_per_minute"] == 900

    def test_zero_rate_returns_400(self, client):
        r = client.put("/reindexer/throttle", json={"rate_per_minute": 0})
        assert r.status_code == 400

    def test_negative_rate_returns_400(self, client):
        r = client.put("/reindexer/throttle", json={"rate_per_minute": -100})
        assert r.status_code == 400

    def test_missing_body_returns_422(self, client):
        r = client.put("/reindexer/throttle", json={})
        assert r.status_code == 422
