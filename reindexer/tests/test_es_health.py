"""Unit tests for the shared ES health cache in app.es.health.

check_es_health itself makes real HTTP calls (httpx.get), so these mock at that
boundary and drive app.es.health's own time.monotonic to control the cache window
deterministically rather than sleeping in real time. Cache reset between tests is
handled globally by conftest.py's autouse _reset_es_health_cache fixture.
"""
from unittest.mock import MagicMock

import app.es.health as health_mod
import pytest


@pytest.fixture
def fake_clock(monkeypatch):
    """A controllable fake time.monotonic, starting at 1000.0."""
    state = {"now": 1000.0}

    def _monotonic():
        return state["now"]

    monkeypatch.setattr(health_mod.time, "monotonic", _monotonic)
    return state


@pytest.fixture
def mock_http(monkeypatch):
    """Mocks httpx.get; call_count reflects real ES calls made (2 per check_all_es_health
    call: one per cluster)."""
    resp = MagicMock()
    resp.json.return_value = {"status": "green"}
    mock_get = MagicMock(return_value=resp)
    monkeypatch.setattr(health_mod.httpx, "get", mock_get)
    return mock_get


class TestCheckAllEsHealthCaching:

    def test_first_call_hits_es_for_both_clusters(self, fake_clock, mock_http):
        health_mod.check_all_es_health()
        assert mock_http.call_count == 2  # collections + granules

    def test_second_call_within_ttl_uses_cache(self, fake_clock, mock_http):
        health_mod.check_all_es_health()
        mock_http.reset_mock()
        health_mod.check_all_es_health()
        mock_http.assert_not_called()

    def test_call_after_ttl_expires_hits_es_again(self, fake_clock, mock_http):
        health_mod.check_all_es_health()
        fake_clock["now"] += health_mod._CACHE_TTL_SECONDS + 0.1
        mock_http.reset_mock()
        health_mod.check_all_es_health()
        assert mock_http.call_count == 2

    def test_cached_result_matches_original(self, fake_clock, mock_http):
        first = health_mod.check_all_es_health()
        second = health_mod.check_all_es_health()
        assert first == second == {"collections": "green", "granules": "green", "overall": "green"}

    def test_many_calls_within_ttl_only_hit_es_once(self, fake_clock, mock_http):
        for _ in range(10):
            health_mod.check_all_es_health()
        assert mock_http.call_count == 2

    def test_multiple_ttl_windows_hit_es_once_per_window(self, fake_clock, mock_http):
        for _ in range(3):
            health_mod.check_all_es_health()
            fake_clock["now"] += health_mod._CACHE_TTL_SECONDS + 0.1
        assert mock_http.call_count == 6  # 2 clusters x 3 fresh windows
