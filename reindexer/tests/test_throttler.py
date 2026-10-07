"""
Unit tests for Throttler.dispatch_in_batches.
"""
from unittest.mock import MagicMock

import pytest

import app.throttler.worker as _worker_mod


@pytest.fixture
def throttler(monkeypatch):
    monkeypatch.setattr(_worker_mod, "publish_indexer_events_batch", MagicMock())
    t = _worker_mod.Throttler()
    t._token_bucket = MagicMock()
    t._token_bucket.consume.return_value = True
    return t


class TestDispatchInBatches:

    def test_slices_by_current_rate_with_shared_stop_and_cancel(self, throttler):
        throttler._token_bucket.current_rate = 2
        records = [("G%d-P" % i, i) for i in range(5)]
        progress = []
        assert throttler.dispatch_in_batches(records, "req-1", on_progress=progress.append) is True
        calls = _worker_mod.publish_indexer_events_batch.call_args_list
        assert [len(c.args[0]) for c in calls] == [2, 2, 1]
        assert progress == [2, 2, 1]
        kwargs = throttler._token_bucket.consume.call_args.kwargs
        assert kwargs["stop_event"] is throttler.stop_event
        throttler.set_cancel_cache(MagicMock(**{"is_cancelled.return_value": True}))
        assert kwargs["cancel_fn"]() is True

    def test_stops_and_returns_false_when_consume_fails(self, throttler):
        throttler._token_bucket.consume.return_value = False
        progress = MagicMock()
        assert throttler.dispatch_in_batches([("G1-P", 1)], "req-1", on_progress=progress) is False
        _worker_mod.publish_indexer_events_batch.assert_not_called()
        progress.assert_not_called()

    def test_empty_records_returns_true_without_publishing(self, throttler):
        assert throttler.dispatch_in_batches([], "req-1") is True
        _worker_mod.publish_indexer_events_batch.assert_not_called()
