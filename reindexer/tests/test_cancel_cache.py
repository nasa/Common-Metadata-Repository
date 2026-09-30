"""
Unit tests for CancelledJobCache.

The background refresh thread is exercised by calling _refresh() directly
to avoid timing sensitivity.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_cancel_cache.py -v
"""
from unittest.mock import MagicMock

import pytest

from app.throttler.cancel_cache import CancelledJobCache


# ---------------------------------------------------------------------------
# Fixture
# ---------------------------------------------------------------------------

@pytest.fixture
def mock_store():
    s = MagicMock()
    s.find_cancelled_jobs.return_value = []
    return s


@pytest.fixture
def cache(mock_store):
    return CancelledJobCache(mock_store, interval_seconds=999)


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestCancelledJobCache:

    def test_is_not_cancelled_initially(self, cache):
        assert not cache.is_cancelled("any-job-id")

    def test_is_cancelled_after_refresh(self, cache, mock_store):
        mock_store.find_cancelled_jobs.return_value = [{"job_id": "job-abc"}]
        cache._refresh()
        assert cache.is_cancelled("job-abc")

    def test_is_not_cancelled_for_non_cancelled_job(self, cache, mock_store):
        mock_store.find_cancelled_jobs.return_value = [{"job_id": "job-abc"}]
        cache._refresh()
        assert not cache.is_cancelled("other-job")

    def test_refresh_removes_stale_cancelled_jobs(self, cache, mock_store):
        mock_store.find_cancelled_jobs.return_value = [{"job_id": "job-abc"}]
        cache._refresh()
        assert cache.is_cancelled("job-abc")

        mock_store.find_cancelled_jobs.return_value = []
        cache._refresh()
        assert not cache.is_cancelled("job-abc")

    def test_refresh_handles_store_exception_gracefully(self, cache, mock_store):
        mock_store.find_cancelled_jobs.side_effect = Exception("DynamoDB down")
        cache._refresh()  # must not raise
        assert not cache.is_cancelled("any-job")

    def test_stop_event_not_set_on_creation(self, cache):
        assert not cache._stop_event.is_set()

    def test_stop_sets_stop_event(self, cache):
        cache.stop()
        assert cache._stop_event.is_set()

    def test_multiple_cancelled_jobs_all_tracked(self, cache, mock_store):
        mock_store.find_cancelled_jobs.return_value = [
            {"job_id": "job-1"},
            {"job_id": "job-2"},
            {"job_id": "job-3"},
        ]
        cache._refresh()
        assert cache.is_cancelled("job-1")
        assert cache.is_cancelled("job-2")
        assert cache.is_cancelled("job-3")
