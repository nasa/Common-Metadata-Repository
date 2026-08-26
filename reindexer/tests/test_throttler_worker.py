"""
Unit tests for ThrottlerWorker._handle_collection, ._handle_granule_page, and ._process.

All external dependencies (Oracle, SQS, ES health) are mocked.
The worker thread is never started — the handler methods are exercised directly.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_throttler_worker.py -v
"""
from unittest.mock import MagicMock

import pytest

import app.throttler.worker as _worker_mod
from app.sqs.schemas import CollectionWorkItem, GranulePageWorkItem


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def worker(monkeypatch):
    """ThrottlerWorker with all external I/O replaced by mocks.
    The background thread is NOT started.
    """
    monkeypatch.setattr(_worker_mod, "db_client", MagicMock())
    monkeypatch.setattr(_worker_mod, "enqueue_page_item", MagicMock())
    monkeypatch.setattr(_worker_mod, "publish_concept_update", MagicMock())
    monkeypatch.setattr(_worker_mod, "delete_message", MagicMock())
    monkeypatch.setattr(_worker_mod, "receive_messages", MagicMock(return_value=[]))
    monkeypatch.setattr(_worker_mod, "check_all_es_health", MagicMock(return_value={"overall": "green"}))
    monkeypatch.setattr(_worker_mod, "job_store", MagicMock())

    from app.throttler.worker import ThrottlerWorker
    w = ThrottlerWorker()
    w._token_bucket = MagicMock()  # prevent real blocking in consume()
    return w


# ---------------------------------------------------------------------------
# Work item helpers
# ---------------------------------------------------------------------------

def _collection(**kw):
    defaults = dict(request_id="req-1", collection_id="C1234-PROV")
    return CollectionWorkItem(**{**defaults, **kw})


def _page(**kw):
    defaults = dict(request_id="req-1", collection_id="C1234-PROV", offset=0, limit=10000)
    return GranulePageWorkItem(**{**defaults, **kw})


_TEST_QUEUE = "http://sqs/test-queue"


def _sqs_msg(item):
    """Wrap a work item in the minimal dict shape the worker receives from SQS."""
    return {"ReceiptHandle": "rh-test", "Body": item.to_json()}


# ---------------------------------------------------------------------------
# _handle_collection — page splitting
# ---------------------------------------------------------------------------

class TestHandleCollection:

    def test_zero_granules_enqueues_nothing(self, worker):
        _worker_mod.db_client.get_granule_count.return_value = 0
        worker._handle_collection(_collection())
        _worker_mod.enqueue_page_item.assert_not_called()

    def test_partial_page_enqueues_one_item(self, worker):
        _worker_mod.db_client.get_granule_count.return_value = 500
        worker._handle_collection(_collection())
        assert _worker_mod.enqueue_page_item.call_count == 1

    def test_exactly_chunk_size_enqueues_one_item(self, worker):
        from app.config import config
        _worker_mod.db_client.get_granule_count.return_value = config.chunk_size
        worker._handle_collection(_collection())
        assert _worker_mod.enqueue_page_item.call_count == 1

    def test_multiple_pages_correct_call_count(self, worker):
        from app.config import config
        _worker_mod.db_client.get_granule_count.return_value = config.chunk_size * 3
        worker._handle_collection(_collection())
        assert _worker_mod.enqueue_page_item.call_count == 3

    def test_page_offsets_are_sequential(self, worker):
        from app.config import config
        count = config.chunk_size * 3
        _worker_mod.db_client.get_granule_count.return_value = count
        worker._handle_collection(_collection())
        offsets = [c.kwargs["offset"] for c in _worker_mod.enqueue_page_item.call_args_list]
        assert offsets == [0, config.chunk_size, config.chunk_size * 2]

    def test_collection_id_forwarded_to_page_items(self, worker):
        _worker_mod.db_client.get_granule_count.return_value = 100
        worker._handle_collection(_collection(collection_id="C9999-TESTPROV"))
        assert _worker_mod.enqueue_page_item.call_args.kwargs["collection_id"] == "C9999-TESTPROV"

    def test_request_id_forwarded_to_page_items(self, worker):
        _worker_mod.db_client.get_granule_count.return_value = 100
        worker._handle_collection(_collection(request_id="special-req"))
        assert _worker_mod.enqueue_page_item.call_args.kwargs["request_id"] == "special-req"

    def test_after_forwarded_to_page_items(self, worker):
        _worker_mod.db_client.get_granule_count.return_value = 100
        worker._handle_collection(_collection(after="2024-01-01T00:00:00Z"))
        assert _worker_mod.enqueue_page_item.call_args.kwargs["after"] == "2024-01-01T00:00:00Z"

    def test_before_forwarded_to_page_items(self, worker):
        _worker_mod.db_client.get_granule_count.return_value = 100
        worker._handle_collection(_collection(before="2024-06-30T23:59:59Z"))
        assert _worker_mod.enqueue_page_item.call_args.kwargs["before"] == "2024-06-30T23:59:59Z"

    def test_increment_collections_split_called_with_granule_count(self, worker):
        _worker_mod.db_client.get_granule_count.return_value = 5000
        worker._handle_collection(_collection(request_id="req-split"))
        _worker_mod.job_store.increment_collections_split.assert_called_once_with("req-split", 5000)

    def test_increment_collections_split_called_even_with_zero_granules(self, worker):
        _worker_mod.db_client.get_granule_count.return_value = 0
        worker._handle_collection(_collection(request_id="req-empty"))
        _worker_mod.job_store.increment_collections_split.assert_called_once_with("req-empty", 0)

    def test_increment_collections_split_called_before_enqueue(self, worker):
        call_order = []
        _worker_mod.job_store.increment_collections_split.side_effect = lambda *a, **k: call_order.append("split")
        _worker_mod.enqueue_page_item.side_effect = lambda **k: call_order.append("enqueue")
        _worker_mod.db_client.get_granule_count.return_value = 100
        worker._handle_collection(_collection())
        assert "enqueue" in call_order
        assert call_order.index("split") < call_order.index("enqueue")


# ---------------------------------------------------------------------------
# _handle_granule_page — publishing
# ---------------------------------------------------------------------------

class TestHandleGranulePage:

    def test_publishes_one_message_per_granule(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = [
            ("G1-PROV", 1), ("G2-PROV", 2), ("G3-PROV", 3),
        ]
        worker._handle_granule_page(_page())
        assert _worker_mod.publish_concept_update.call_count == 3

    def test_publishes_correct_concept_id_revision_and_request_id(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = [("G5-PROV", 7)]
        worker._handle_granule_page(_page(request_id="req-42"))
        _worker_mod.publish_concept_update.assert_called_once_with("G5-PROV", 7, "req-42")

    def test_empty_page_publishes_nothing(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = []
        worker._handle_granule_page(_page())
        _worker_mod.publish_concept_update.assert_not_called()

    def test_empty_page_skips_token_bucket(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = []
        worker._handle_granule_page(_page())
        worker._token_bucket.consume.assert_not_called()

    def test_token_bucket_consumed_with_full_page_size(self, worker):
        granules = [("G%d-P" % i, i) for i in range(5)]
        _worker_mod.db_client.get_granule_ids.return_value = granules
        worker._handle_granule_page(_page())
        worker._token_bucket.consume.assert_called_once_with(5, stop_event=worker._stop_event)

    def test_date_params_forwarded_to_db(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = []
        worker._handle_granule_page(_page(after="2024-01-01T00:00:00Z", before="2024-06-30T23:59:59Z"))
        kw = _worker_mod.db_client.get_granule_ids.call_args.kwargs
        assert kw["after"] == "2024-01-01T00:00:00Z"
        assert kw["before"] == "2024-06-30T23:59:59Z"

    def test_offset_and_limit_forwarded_to_db(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = []
        worker._handle_granule_page(_page(offset=20000, limit=5000))
        pos = _worker_mod.db_client.get_granule_ids.call_args.args
        assert pos[1] == 20000  # offset
        assert pos[2] == 5000   # limit


# ---------------------------------------------------------------------------
# _process — dispatch routing and delete semantics
# ---------------------------------------------------------------------------

class TestProcess:

    def test_successful_collection_item_deletes_message(self, worker):
        _worker_mod.db_client.get_granule_count.return_value = 0
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        _worker_mod.delete_message.assert_called_once()

    def test_successful_granule_page_item_deletes_message(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = []
        worker._process(_sqs_msg(_page()), _TEST_QUEUE)
        _worker_mod.delete_message.assert_called_once()

    def test_processing_error_does_not_delete(self, worker):
        _worker_mod.db_client.get_granule_ids.side_effect = RuntimeError("db down")
        worker._process(_sqs_msg(_page()), _TEST_QUEUE)
        _worker_mod.delete_message.assert_not_called()

    def test_malformed_json_deleted_as_poison_pill(self, worker):
        bad = {"ReceiptHandle": "rh-xyz", "Body": "not-json{{{"}
        worker._process(bad, _TEST_QUEUE)
        _worker_mod.delete_message.assert_called_once()

    def test_unknown_work_item_type_deleted_as_poison_pill(self, worker):
        import json
        bad = {"ReceiptHandle": "rh-xyz", "Body": json.dumps({"type": "unknown-type", "request_id": "r"})}
        worker._process(bad, _TEST_QUEUE)
        _worker_mod.delete_message.assert_called_once()

    def test_parse_error_dispatches_no_work(self, worker):
        bad = {"ReceiptHandle": "rh-xyz", "Body": "not-json{{{"}
        worker._process(bad, _TEST_QUEUE)
        _worker_mod.publish_concept_update.assert_not_called()
        _worker_mod.enqueue_page_item.assert_not_called()

    def test_collection_item_routes_to_handle_collection(self, worker):
        _worker_mod.db_client.get_granule_count.return_value = 0
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        _worker_mod.db_client.get_granule_count.assert_called_once()
        _worker_mod.db_client.get_granule_ids.assert_not_called()

    def test_granule_page_item_routes_to_handle_granule_page(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = []
        worker._process(_sqs_msg(_page()), _TEST_QUEUE)
        _worker_mod.db_client.get_granule_ids.assert_called_once()
        _worker_mod.db_client.get_granule_count.assert_not_called()

    def test_delete_uses_correct_receipt_handle(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = []
        msg = {"ReceiptHandle": "my-receipt-handle-123", "Body": _page().to_json()}
        worker._process(msg, _TEST_QUEUE)
        assert "my-receipt-handle-123" in _worker_mod.delete_message.call_args.args

    def test_delete_targets_source_queue_not_hardcoded_intermediate(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = []
        source = "http://sqs/collection-queue"
        worker._process(_sqs_msg(_page()), source)
        assert _worker_mod.delete_message.call_args.args[0] == source


# ---------------------------------------------------------------------------
# Graceful shutdown — stop() and SIGTERM behaviour
# ---------------------------------------------------------------------------

class TestGracefulShutdown:

    def test_stop_event_not_set_on_creation(self, worker):
        assert not worker._stop_event.is_set()

    def test_stop_sets_stop_event(self, worker):
        worker.stop()
        assert worker._stop_event.is_set()

    def test_stop_marks_interrupted_even_when_thread_clears_job_id_during_join(self, worker):
        """Verify job_id is captured before thread.join(), not after."""
        worker._current_job_id = "job-123"

        def fake_join(timeout):
            with worker._job_lock:
                worker._current_job_id = None  # simulates thread's finally block

        worker._thread = MagicMock()
        worker._thread.is_alive.return_value = True
        worker._thread.join.side_effect = fake_join

        worker.stop()

        _worker_mod.job_store.mark_job.assert_called_with("job-123", "interrupted")

    def test_stop_does_not_mark_interrupted_when_no_job_in_flight(self, worker):
        worker._current_job_id = None
        worker._thread = MagicMock()
        worker._thread.is_alive.return_value = False
        worker.stop()
        _worker_mod.job_store.mark_job.assert_not_called()


# ---------------------------------------------------------------------------
# try_complete_job called after split and dispatch
# ---------------------------------------------------------------------------

class TestEsHealthGating:

    def _stop_via_receive(self, worker):
        """Side-effect for receive_messages that stops the worker after the first call."""
        def _stopper(*args, **kwargs):
            worker._stop_event.set()
            return []
        return _stopper

    def test_es_health_not_checked_before_interval_expires(self, worker, monkeypatch):
        import time
        monkeypatch.setattr(_worker_mod, "check_all_es_health", MagicMock(return_value={"overall": "green"}))
        worker._last_es_check = time.monotonic()  # just checked
        worker._last_es_health = {"overall": "green", "collections": "green", "granules": "green"}
        _worker_mod.receive_messages.side_effect = self._stop_via_receive(worker)

        worker._run()

        _worker_mod.check_all_es_health.assert_not_called()

    def test_es_health_checked_after_interval_expires(self, worker, monkeypatch):
        import time
        monkeypatch.setattr(_worker_mod, "check_all_es_health", MagicMock(return_value={"overall": "green"}))
        worker._last_es_check = time.monotonic() - 31.0  # expired
        worker._last_es_health = {"overall": "green", "collections": "green", "granules": "green"}
        _worker_mod.receive_messages.side_effect = self._stop_via_receive(worker)

        worker._run()

        _worker_mod.check_all_es_health.assert_called_once()


class TestJobCompletionDetection:

    def test_try_complete_called_after_collection_split(self, worker):
        _worker_mod.db_client.get_granule_count.return_value = 0
        worker._handle_collection(_collection())
        _worker_mod.job_store.try_complete_job.assert_called_with("req-1")

    def test_try_complete_called_after_granule_page_dispatch(self, worker):
        granules = [("G1-P", 1), ("G2-P", 2)]
        _worker_mod.db_client.get_granule_ids.return_value = granules
        worker._token_bucket.consume.return_value = True
        worker._handle_granule_page(_page())
        _worker_mod.job_store.try_complete_job.assert_called_with("req-1")

    def test_try_complete_not_called_when_stop_event_fires_in_consume(self, worker):
        granules = [("G1-P", 1)]
        _worker_mod.db_client.get_granule_ids.return_value = granules
        worker._token_bucket.consume.return_value = False  # stop event fired
        worker._handle_granule_page(_page())
        _worker_mod.job_store.try_complete_job.assert_not_called()

    def test_stop_event_passed_to_consume(self, worker):
        granules = [("G1-P", 1)]
        _worker_mod.db_client.get_granule_ids.return_value = granules
        worker._token_bucket.consume.return_value = True
        worker._handle_granule_page(_page())
        _, kwargs = worker._token_bucket.consume.call_args
        assert kwargs.get("stop_event") is worker._stop_event

    def test_thread_exits_cleanly_after_stop(self, worker):
        worker.start()
        assert worker._thread.is_alive()
        worker.stop()
        assert not worker._thread.is_alive()

    def test_stop_is_idempotent(self, worker):
        worker.stop()
        worker.stop()  # second call must not raise
        assert worker._stop_event.is_set()

    def test_stop_before_start_does_not_raise(self, worker):
        # stop() called with no thread running must be a no-op
        worker.stop()

    def test_current_job_id_is_none_initially(self, worker):
        assert worker.current_job_id is None

    def test_stop_marks_current_job_as_interrupted(self, worker):
        worker._current_job_id = "active-job-123"
        worker.stop()
        _worker_mod.job_store.mark_job.assert_called_once_with("active-job-123", "interrupted")

    def test_stop_without_active_job_does_not_call_mark_job(self, worker):
        worker.stop()
        _worker_mod.job_store.mark_job.assert_not_called()

    def test_current_job_id_cleared_after_successful_process(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = []
        worker._process(_sqs_msg(_page()), _TEST_QUEUE)
        assert worker.current_job_id is None

    def test_current_job_id_set_during_processing(self, worker):
        # Verify it's set to the item's request_id inside _process by checking
        # the granule_ids call (which happens while _current_job_id is set)
        captured = []

        def _capture_job_id(*args, **kwargs):
            captured.append(worker.current_job_id)
            return []

        _worker_mod.db_client.get_granule_ids.side_effect = _capture_job_id
        worker._process(_sqs_msg(_page(request_id="req-capture")), _TEST_QUEUE)
        assert captured == ["req-capture"]


# ---------------------------------------------------------------------------
# Cancellation — _process skips cancelled jobs
# ---------------------------------------------------------------------------

class TestCancellation:

    def _with_cancel_cache(self, worker, cancelled=True):
        cache = MagicMock()
        cache.is_cancelled.return_value = cancelled
        worker.set_cancel_cache(cache)
        return cache

    def test_cancelled_item_deletes_message_without_processing(self, worker):
        self._with_cancel_cache(worker, cancelled=True)
        _worker_mod.db_client.get_granule_count.return_value = 0
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        _worker_mod.delete_message.assert_called_once()
        _worker_mod.db_client.get_granule_count.assert_not_called()

    def test_cancelled_item_does_not_enqueue_pages(self, worker):
        self._with_cancel_cache(worker, cancelled=True)
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        _worker_mod.enqueue_page_item.assert_not_called()

    def test_non_cancelled_item_processes_normally(self, worker):
        self._with_cancel_cache(worker, cancelled=False)
        _worker_mod.db_client.get_granule_count.return_value = 0
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        _worker_mod.db_client.get_granule_count.assert_called_once()

    def test_no_cancel_cache_processes_normally(self, worker):
        # _cancel_cache is None by default — should not crash
        _worker_mod.db_client.get_granule_ids.return_value = []
        worker._process(_sqs_msg(_page()), _TEST_QUEUE)
        _worker_mod.db_client.get_granule_ids.assert_called_once()

    def test_granule_page_dispatched_count_reported_to_job_store(self, worker):
        _worker_mod.db_client.get_granule_ids.return_value = [
            ("G1-P", 1), ("G2-P", 2),
        ]
        worker._process(_sqs_msg(_page(request_id="req-dispatch")), _TEST_QUEUE)
        _worker_mod.job_store.update_dispatched.assert_called_once_with("req-dispatch", 2)
