"""
Unit tests for ThrottlerWorker._handle_collection (Option C streaming model)
and ._process.

All external dependencies (Oracle, SQS, DynamoDB, ES health) are mocked.
The worker thread is never started except in the few tests that explicitly
exercise start()/stop().

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_throttler_worker.py -v
"""
from unittest.mock import ANY, MagicMock

import pytest

import app.throttler.worker as _worker_mod
from app.sqs.schemas import CollectionWorkItem


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def worker(monkeypatch):
    """ThrottlerWorker with all external I/O replaced by mocks.
    The background thread is NOT started.
    """
    monkeypatch.setattr(_worker_mod, "db_client", MagicMock())
    monkeypatch.setattr(_worker_mod, "publish_concept_updates_batch", MagicMock())
    monkeypatch.setattr(_worker_mod, "delete_message", MagicMock())
    monkeypatch.setattr(_worker_mod, "receive_messages", MagicMock(return_value=[]))
    monkeypatch.setattr(_worker_mod, "check_all_es_health", MagicMock(return_value={"overall": "green"}))
    monkeypatch.setattr(_worker_mod, "job_store", MagicMock())
    monkeypatch.setattr(_worker_mod, "checkpoint_store", MagicMock())

    # Default: no checkpoint (fresh collection)
    _worker_mod.checkpoint_store.get_collection_checkpoint.return_value = None

    from app.throttler.worker import ThrottlerWorker
    w = ThrottlerWorker()
    w._token_bucket = MagicMock()
    w._token_bucket.consume.return_value = True  # don't block by default
    return w


# ---------------------------------------------------------------------------
# Work item helpers
# ---------------------------------------------------------------------------

def _collection(**kw):
    defaults = dict(request_id="req-1", collection_id="C1234-PROV")
    return CollectionWorkItem(**{**defaults, **kw})


_TEST_QUEUE = "http://sqs/test-queue"


def _sqs_msg(item):
    """Wrap a work item in the minimal dict shape the worker receives from SQS."""
    return {"ReceiptHandle": "rh-test", "Body": item.to_json()}


def _make_chunks(*chunks):
    """Configure stream_granule_ids to yield the given chunks."""
    _worker_mod.db_client.stream_granule_ids.return_value = iter(chunks)


# ---------------------------------------------------------------------------
# _handle_collection — streaming dispatch
# ---------------------------------------------------------------------------

class TestHandleCollection:

    def test_zero_granules_no_dispatch(self, worker):
        _make_chunks()
        worker._handle_collection(_collection())
        _worker_mod.publish_concept_updates_batch.assert_not_called()

    def test_zero_granules_increment_collections_split_called_with_zero(self, worker):
        _make_chunks()
        worker._handle_collection(_collection(request_id="req-1"))
        _worker_mod.job_store.increment_collections_split.assert_called_once_with("req-1", 0)

    def test_zero_granules_try_complete_called(self, worker):
        _make_chunks()
        worker._handle_collection(_collection(request_id="req-1"))
        _worker_mod.job_store.try_complete_job.assert_called_once_with("req-1")

    def test_zero_granules_checkpoint_deleted(self, worker):
        _make_chunks()
        worker._handle_collection(_collection())
        _worker_mod.checkpoint_store.delete_collection_checkpoint.assert_called_once()

    def test_single_chunk_dispatched(self, worker):
        granules = [("G1-PROV", 1), ("G2-PROV", 2)]
        _make_chunks(granules)
        worker._handle_collection(_collection())
        _worker_mod.publish_concept_updates_batch.assert_called_once_with(granules, "req-1")

    def test_multiple_chunks_all_dispatched(self, worker):
        c1 = [("G1-PROV", 1), ("G2-PROV", 2)]
        c2 = [("G3-PROV", 3)]
        _make_chunks(c1, c2)
        worker._handle_collection(_collection())
        assert _worker_mod.publish_concept_updates_batch.call_count == 2

    def test_token_bucket_consumed_once_per_chunk(self, worker):
        c1 = [("G1-PROV", 1), ("G2-PROV", 2)]
        c2 = [("G3-PROV", 3)]
        _make_chunks(c1, c2)
        worker._handle_collection(_collection())
        assert worker._token_bucket.consume.call_count == 2

    def test_token_bucket_consume_called_with_chunk_length(self, worker):
        granules = [("G%d-P" % i, i) for i in range(7)]
        _make_chunks(granules)
        worker._handle_collection(_collection())
        worker._token_bucket.consume.assert_called_once_with(
            7, stop_event=worker._stop_event, cancel_fn=ANY
        )

    def test_checkpoint_written_after_each_chunk(self, worker):
        c1 = [("G1-PROV", 1), ("G2-PROV", 2)]
        c2 = [("G3-PROV", 3)]
        _make_chunks(c1, c2)
        worker._handle_collection(_collection())
        assert _worker_mod.checkpoint_store.write_collection_checkpoint.call_count == 2

    def test_checkpoint_last_concept_id_is_last_in_chunk(self, worker):
        chunk = [("G1-PROV", 1), ("G5-PROV", 5)]
        _make_chunks(chunk)
        worker._handle_collection(_collection(request_id="req-1", collection_id="C1-PROV"))
        args = _worker_mod.checkpoint_store.write_collection_checkpoint.call_args.args
        # signature: (job_id, collection_id, last_concept_id, granules_dispatched, chunks_dispatched)
        assert args[2] == "G5-PROV"

    def test_checkpoint_deleted_on_full_completion(self, worker):
        _make_chunks([("G1-PROV", 1)])
        worker._handle_collection(_collection())
        _worker_mod.checkpoint_store.delete_collection_checkpoint.assert_called_once()

    def test_increment_collections_split_with_full_collection_count_fresh_run(self, worker):
        _make_chunks([("G1-PROV", 1), ("G2-PROV", 2)], [("G3-PROV", 3)])
        worker._handle_collection(_collection(request_id="req-1"))
        _worker_mod.job_store.increment_collections_split.assert_called_once_with("req-1", 3)

    def test_increment_collections_split_uses_total_dispatched_not_tail_on_resume(self, worker):
        """On resume, increment_collections_split must receive the full collection count
        (prior dispatched + this run), not just the post-checkpoint tail, so that
        total_granules_expected in the job record reflects the real collection size."""
        _worker_mod.checkpoint_store.get_collection_checkpoint.return_value = {
            "last_concept_id": "G60-PROV",
            "granules_dispatched": 60,
            "chunks_dispatched": 2,
        }
        # Only 40 granules remain after the checkpoint
        _make_chunks([("G61-PROV", 61)] * 40)
        worker._handle_collection(_collection(request_id="req-1"))
        # Should report 100 (60 prior + 40 this run), not 40
        _worker_mod.job_store.increment_collections_split.assert_called_once_with("req-1", 100)

    def test_update_dispatched_called_per_chunk_with_correct_counts(self, worker):
        _make_chunks([("G1-PROV", 1), ("G2-PROV", 2)], [("G3-PROV", 3)])
        worker._handle_collection(_collection(request_id="req-1"))
        calls = _worker_mod.job_store.update_dispatched.call_args_list
        assert len(calls) == 2
        assert calls[0].args == ("req-1", 2)
        assert calls[1].args == ("req-1", 1)

    def test_try_complete_job_called_at_end(self, worker):
        _make_chunks([("G1-PROV", 1)])
        worker._handle_collection(_collection(request_id="req-1"))
        _worker_mod.job_store.try_complete_job.assert_called_once_with("req-1")

    def test_stream_chunk_size_from_config_passed_to_db(self, worker):
        from app.config import config
        _make_chunks()
        worker._handle_collection(_collection())
        kw = _worker_mod.db_client.stream_granule_ids.call_args.kwargs
        assert kw.get("chunk_size") == config.stream_chunk_size

    def test_after_and_before_forwarded_to_db(self, worker):
        _make_chunks()
        worker._handle_collection(_collection(after="2024-01-01T00:00:00Z", before="2024-06-30T23:59:59Z"))
        kw = _worker_mod.db_client.stream_granule_ids.call_args.kwargs
        assert kw.get("after") == "2024-01-01T00:00:00Z"
        assert kw.get("before") == "2024-06-30T23:59:59Z"

    def test_collection_id_forwarded_to_db(self, worker):
        _make_chunks()
        worker._handle_collection(_collection(collection_id="C9999-TESTPROV"))
        kw = _worker_mod.db_client.stream_granule_ids.call_args
        assert kw.args[0] == "C9999-TESTPROV"

    # ------------------------------------------------------------------
    # Resume from checkpoint
    # ------------------------------------------------------------------

    def test_resume_from_checkpoint_uses_last_concept_id(self, worker):
        _worker_mod.checkpoint_store.get_collection_checkpoint.return_value = {
            "last_concept_id": "G50-PROV",
            "granules_dispatched": 100,
            "chunks_dispatched": 2,
        }
        _make_chunks([("G51-PROV", 51)])
        worker._handle_collection(_collection())
        kw = _worker_mod.db_client.stream_granule_ids.call_args.kwargs
        assert kw.get("start_after_concept_id") == "G50-PROV"

    def test_no_checkpoint_passes_none_start_after(self, worker):
        _worker_mod.checkpoint_store.get_collection_checkpoint.return_value = None
        _make_chunks()
        worker._handle_collection(_collection())
        kw = _worker_mod.db_client.stream_granule_ids.call_args.kwargs
        assert kw.get("start_after_concept_id") is None

    def test_resume_adds_to_checkpoint_granule_count(self, worker):
        """total_dispatched in checkpoint grows from prior run's baseline."""
        _worker_mod.checkpoint_store.get_collection_checkpoint.return_value = {
            "last_concept_id": "G50-PROV",
            "granules_dispatched": 100,
            "chunks_dispatched": 2,
        }
        _make_chunks([("G51-PROV", 51), ("G52-PROV", 52)])
        worker._handle_collection(_collection(request_id="req-1", collection_id="C1-PROV"))
        args = _worker_mod.checkpoint_store.write_collection_checkpoint.call_args.args
        # granules_dispatched should be 100 (prior) + 2 (this chunk) = 102
        assert args[3] == 102

    # ------------------------------------------------------------------
    # Interrupt / cancel mid-stream
    # ------------------------------------------------------------------

    def test_stop_event_mid_stream_raises_collection_interrupted(self, worker):
        from app.throttler.worker import _CollectionInterrupted
        _make_chunks([("G1-PROV", 1)])
        worker._token_bucket.consume.return_value = False
        worker._stop_event.set()
        with pytest.raises(_CollectionInterrupted):
            worker._handle_collection(_collection())

    def test_stop_event_mid_stream_does_not_delete_checkpoint(self, worker):
        from app.throttler.worker import _CollectionInterrupted
        _make_chunks([("G1-PROV", 1)])
        worker._token_bucket.consume.return_value = False
        worker._stop_event.set()
        with pytest.raises(_CollectionInterrupted):
            worker._handle_collection(_collection())
        _worker_mod.checkpoint_store.delete_collection_checkpoint.assert_not_called()

    def test_stop_event_mid_stream_does_not_try_complete(self, worker):
        from app.throttler.worker import _CollectionInterrupted
        _make_chunks([("G1-PROV", 1)])
        worker._token_bucket.consume.return_value = False
        worker._stop_event.set()
        with pytest.raises(_CollectionInterrupted):
            worker._handle_collection(_collection())
        _worker_mod.job_store.try_complete_job.assert_not_called()

    def test_cancel_fn_fires_returns_normally_no_raise(self, worker):
        """When cancel_fn fires, _handle_collection returns (no raise) so message is deleted."""
        _make_chunks([("G1-PROV", 1)])
        worker._token_bucket.consume.return_value = False
        # stop_event NOT set → cancel_fn path
        worker._handle_collection(_collection())  # should not raise

    def test_cancellation_check_before_consume_skips_token_wait(self, worker):
        """is_job_cancelled checked before consume() so a cancelled job skips the token wait."""
        cache = MagicMock()
        cache.is_cancelled.return_value = True
        worker.set_cancel_cache(cache)
        _make_chunks([("G1-PROV", 1)])
        worker._handle_collection(_collection())
        worker._token_bucket.consume.assert_not_called()

    def test_cancel_mid_stream_does_not_publish(self, worker):
        cache = MagicMock()
        cache.is_cancelled.return_value = True
        worker.set_cancel_cache(cache)
        _make_chunks([("G1-PROV", 1)])
        worker._handle_collection(_collection())
        _worker_mod.publish_concept_updates_batch.assert_not_called()


# ---------------------------------------------------------------------------
# _process — dispatch routing and delete semantics
# ---------------------------------------------------------------------------

class TestProcess:

    def test_successful_collection_item_deletes_message(self, worker):
        _make_chunks()
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        _worker_mod.delete_message.assert_called_once()

    def test_processing_error_does_not_delete(self, worker):
        _worker_mod.db_client.stream_granule_ids.side_effect = RuntimeError("db down")
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
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
        _worker_mod.publish_concept_updates_batch.assert_not_called()

    def test_collection_item_routes_to_handle_collection(self, worker):
        _make_chunks()
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        _worker_mod.db_client.stream_granule_ids.assert_called_once()

    def test_delete_uses_correct_receipt_handle(self, worker):
        _make_chunks()
        msg = {"ReceiptHandle": "my-receipt-handle-123", "Body": _collection().to_json()}
        worker._process(msg, _TEST_QUEUE)
        assert "my-receipt-handle-123" in _worker_mod.delete_message.call_args.args

    def test_delete_targets_source_queue(self, worker):
        _make_chunks()
        source = "http://sqs/collection-queue"
        worker._process(_sqs_msg(_collection()), source)
        assert _worker_mod.delete_message.call_args.args[0] == source

    def test_collection_interrupted_does_not_delete_message(self, worker):
        """When _CollectionInterrupted is raised, SQS message is kept for re-delivery."""
        from app.throttler.worker import _CollectionInterrupted
        _worker_mod.db_client.stream_granule_ids.return_value = iter([[("G1-PROV", 1)]])
        worker._token_bucket.consume.return_value = False
        worker._stop_event.set()
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        _worker_mod.delete_message.assert_not_called()


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

        _worker_mod.job_store.try_mark_interrupted.assert_called_with("job-123")

    def test_stop_does_not_mark_interrupted_when_no_job_in_flight(self, worker):
        worker._current_job_id = None
        worker._thread = MagicMock()
        worker._thread.is_alive.return_value = False
        worker.stop()
        _worker_mod.job_store.mark_job.assert_not_called()


# ---------------------------------------------------------------------------
# ES health gating
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


# ---------------------------------------------------------------------------
# Job completion and thread lifecycle
# ---------------------------------------------------------------------------

class TestJobCompletionDetection:

    def test_try_complete_called_after_collection_streamed(self, worker):
        _make_chunks()
        worker._handle_collection(_collection())
        _worker_mod.job_store.try_complete_job.assert_called_with("req-1")

    def test_try_complete_not_called_when_stop_event_fires(self, worker):
        from app.throttler.worker import _CollectionInterrupted
        _make_chunks([("G1-P", 1)])
        worker._token_bucket.consume.return_value = False
        worker._stop_event.set()
        with pytest.raises(_CollectionInterrupted):
            worker._handle_collection(_collection())
        _worker_mod.job_store.try_complete_job.assert_not_called()

    def test_stop_event_passed_to_consume(self, worker):
        _make_chunks([("G1-P", 1)])
        worker._handle_collection(_collection())
        _, kwargs = worker._token_bucket.consume.call_args
        assert kwargs.get("stop_event") is worker._stop_event

    def test_thread_exits_cleanly_after_stop(self, worker, monkeypatch):
        # Patch config so stream_chunk_size <= rate_per_minute for start() assertion
        from app import config as cfg_mod
        monkeypatch.setattr(cfg_mod.config, "stream_chunk_size", 100)
        monkeypatch.setattr(cfg_mod.config, "rate_per_minute", 600)
        worker.start()
        assert worker._thread.is_alive()
        worker.stop()
        assert not worker._thread.is_alive()

    def test_stop_is_idempotent(self, worker):
        worker.stop()
        worker.stop()  # second call must not raise
        assert worker._stop_event.is_set()

    def test_stop_before_start_does_not_raise(self, worker):
        worker.stop()

    def test_current_job_id_is_none_initially(self, worker):
        assert worker.current_job_id is None

    def test_stop_marks_current_job_as_interrupted(self, worker):
        worker._current_job_id = "active-job-123"
        worker.stop()
        _worker_mod.job_store.try_mark_interrupted.assert_called_once_with("active-job-123")

    def test_stop_without_active_job_does_not_call_mark_job(self, worker):
        worker.stop()
        _worker_mod.job_store.mark_job.assert_not_called()

    def test_current_job_id_cleared_after_successful_process(self, worker):
        _make_chunks()
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        assert worker.current_job_id is None

    def test_current_job_id_set_during_streaming(self, worker):
        """_current_job_id is set to item.request_id while _handle_collection runs."""
        captured = []

        def _capture(*args, **kwargs):
            captured.append(worker.current_job_id)
            return iter([])

        _worker_mod.db_client.stream_granule_ids.side_effect = _capture
        worker._process(_sqs_msg(_collection(request_id="req-capture")), _TEST_QUEUE)
        assert captured == ["req-capture"]


# ---------------------------------------------------------------------------
# Startup assertion — stream_chunk_size vs rate_per_minute
# ---------------------------------------------------------------------------

class TestStartupAssertion:

    def test_start_raises_when_chunk_size_exceeds_rate(self, worker, monkeypatch):
        from app import config as cfg_mod
        monkeypatch.setattr(cfg_mod.config, "stream_chunk_size", 2000)
        monkeypatch.setattr(cfg_mod.config, "rate_per_minute", 600)
        with pytest.raises(ValueError, match="STREAM_CHUNK_SIZE"):
            worker.start()

    def test_start_succeeds_when_chunk_size_equals_rate(self, worker, monkeypatch):
        from app import config as cfg_mod
        monkeypatch.setattr(cfg_mod.config, "stream_chunk_size", 600)
        monkeypatch.setattr(cfg_mod.config, "rate_per_minute", 600)
        worker.start()
        worker.stop()

    def test_start_succeeds_when_chunk_size_below_rate(self, worker, monkeypatch):
        from app import config as cfg_mod
        monkeypatch.setattr(cfg_mod.config, "stream_chunk_size", 500)
        monkeypatch.setattr(cfg_mod.config, "rate_per_minute", 600)
        worker.start()
        worker.stop()


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
        _make_chunks()
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        _worker_mod.delete_message.assert_called_once()
        _worker_mod.db_client.stream_granule_ids.assert_not_called()

    def test_non_cancelled_item_processes_normally(self, worker):
        self._with_cancel_cache(worker, cancelled=False)
        _make_chunks()
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        _worker_mod.db_client.stream_granule_ids.assert_called_once()

    def test_no_cancel_cache_processes_normally(self, worker):
        # _cancel_cache is None by default — should not crash
        _make_chunks()
        worker._process(_sqs_msg(_collection()), _TEST_QUEUE)
        _worker_mod.db_client.stream_granule_ids.assert_called_once()

    def test_granule_streaming_dispatched_count_reported_to_job_store(self, worker):
        _make_chunks([("G1-P", 1), ("G2-P", 2)])
        worker._process(_sqs_msg(_collection(request_id="req-dispatch")), _TEST_QUEUE)
        _worker_mod.job_store.update_dispatched.assert_called_with("req-dispatch", 2)

    def test_cancel_mid_stream_does_not_delete_checkpoint(self, worker):
        """When a job is cancelled mid-stream, the DynamoDB checkpoint must NOT be deleted.
        The checkpoint marks work-in-progress state; deleting it would lose the resume cursor.
        The caller (operator / cancel API) is responsible for cleaning up orphaned checkpoints
        on a full cancel, but the worker itself must leave the checkpoint intact on cancel."""
        cache = MagicMock()
        cache.is_cancelled.return_value = True
        worker.set_cancel_cache(cache)
        _make_chunks([("G1-PROV", 1), ("G2-PROV", 2)])
        worker._handle_collection(_collection())
        _worker_mod.checkpoint_store.delete_collection_checkpoint.assert_not_called()
