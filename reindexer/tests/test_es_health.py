"""The shared ES health cache, with httpx.get mocked and a fake monotonic clock."""
from unittest.mock import MagicMock

import app.es.health as health_mod
import pytest


@pytest.fixture
def fake_clock(monkeypatch):
    state = {"now": 1000.0}
    monkeypatch.setattr(health_mod.time, "monotonic", lambda: state["now"])
    return state


@pytest.fixture
def mock_http(monkeypatch):
    """Two real ES calls per check_all_es_health: one per cluster."""
    resp = MagicMock()
    resp.json.return_value = {"status": "green"}
    mock_get = MagicMock(return_value=resp)
    monkeypatch.setattr(health_mod.httpx, "get", mock_get)
    return mock_get


class TestCheckAllEsHealthCaching:

    def test_calls_within_ttl_share_one_check(self, fake_clock, mock_http):
        results = [health_mod.check_all_es_health() for _ in range(5)]
        assert mock_http.call_count == 2
        assert all(r == {"collections": "green", "granules": "green", "overall": "green"} for r in results)

    def test_each_expired_window_checks_again(self, fake_clock, mock_http):
        for _ in range(3):
            health_mod.check_all_es_health()
            fake_clock["now"] += health_mod._CACHE_TTL_SECONDS + 0.1
        assert mock_http.call_count == 6
