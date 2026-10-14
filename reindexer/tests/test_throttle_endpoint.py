"""
Unit tests for GET /throttle and PUT /throttle, with the throttler mocked.
"""
from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient


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
    m.get_rate.return_value = 1200.0
    monkeypatch.setattr("app.routers.throttle.throttler", m)
    return m


def test_get_returns_current_rate(client):
    """When the throttle is read, then the current rate is returned."""
    assert client.get("/reindexer/throttle").json()["rate_per_minute"] == pytest.approx(1200.0)


def test_put_sets_and_returns_the_rate(client, mock_throttler):
    """When the throttle is set, then the new rate is applied and returned."""
    r = client.put("/reindexer/throttle", json={"rate_per_minute": 450})
    assert (r.status_code, r.json()["rate_per_minute"]) == (200, 450)
    mock_throttler.set_rate.assert_called_once_with(450)


def test_put_zero_rate_returns_400(client, mock_throttler):
    """When the rate is set to 0, then 400 and the rate is unchanged."""
    assert client.put("/reindexer/throttle", json={"rate_per_minute": 0}).status_code == 400
    mock_throttler.set_rate.assert_not_called()
