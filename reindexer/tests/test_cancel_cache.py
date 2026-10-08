"""
Unit tests for CancelledJobCache. _refresh() is called directly, so no timing.
"""
from unittest.mock import MagicMock

from app.throttler.cancel_cache import CancelledJobCache


def _cache():
    store = MagicMock()
    return CancelledJobCache(store, interval_seconds=999), store


def test_refresh_replaces_the_set():
    cache, store = _cache()
    store.find_cancelled_jobs.return_value = [{"job_id": "j1"}, {"job_id": "j2"}]
    cache._refresh()
    assert cache.is_cancelled("j1") and cache.is_cancelled("j2")
    assert not cache.is_cancelled("other")

    store.find_cancelled_jobs.return_value = []
    cache._refresh()
    assert not cache.is_cancelled("j1")


def test_failed_refresh_keeps_the_previous_set():
    cache, store = _cache()
    store.find_cancelled_jobs.return_value = [{"job_id": "j1"}]
    cache._refresh()
    store.find_cancelled_jobs.side_effect = Exception("DynamoDB down")
    cache._refresh()
    assert cache.is_cancelled("j1")


def test_refreshes_only_while_holding_a_job(monkeypatch):
    import app.throttler.cancel_cache as mod
    cache, store = _cache()
    store.find_cancelled_jobs.return_value = []
    held = [set(), {"j1"}]
    monkeypatch.setattr(mod.leases, "held_jobs", lambda: held.pop(0))
    waits = iter([False, False, True])  # two ticks, then stop
    monkeypatch.setattr(cache._stop_event, "wait", lambda timeout: next(waits))
    cache._run()
    store.find_cancelled_jobs.assert_called_once()
