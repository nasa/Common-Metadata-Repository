"""
Unit tests for the id-range granule scan (app.throttler.id_range_scanner).

All external dependencies (Oracle, DynamoDB, ES health, the throttler singleton)
are replaced with mocks. _run() is called directly (not via a thread) so the loop
executes synchronously and deterministically.
"""
import threading
from unittest.mock import ANY, MagicMock, call

import pytest

import app.throttler.id_range_scanner as _scanner_mod
from app.throttler.id_range_scanner import (
    _acquire_scan_slot,
    _dedup_latest_revision,
    _run,
    _wait_for_green_or_signal,
    start_id_range_scan,
)

_AFTER, _BEFORE = "2024-01-01T00:00:00Z", "2024-06-01T00:00:00Z"


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def deps(monkeypatch):
    """Replace every external dependency _run() touches with a mock."""
    db = MagicMock()
    job_store = MagicMock()
    throttler = MagicMock()
    throttler.is_job_cancelled.return_value = False
    throttler.stop_event = threading.Event()
    throttler.stop_event.wait = MagicMock()  # never actually sleep between ES polls
    throttler.dispatch_in_batches.return_value = True

    def _simulate_dispatch(records, request_id, on_progress=None):
        """Returns return_value and reports the whole chunk as one sub-batch."""
        result = throttler.dispatch_in_batches.return_value
        if result and on_progress is not None:
            on_progress(len(records))
        return result

    throttler.dispatch_in_batches.side_effect = _simulate_dispatch

    monkeypatch.setattr(_scanner_mod, "db_client", db)
    monkeypatch.setattr(_scanner_mod, "job_store", job_store)
    monkeypatch.setattr(_scanner_mod, "throttler", throttler)
    monkeypatch.setattr(_scanner_mod, "check_all_es_health", MagicMock(return_value={"overall": "green"}))
    monkeypatch.setattr(_scanner_mod.leases, "_held", set())

    return db, job_store, throttler


# ---------------------------------------------------------------------------
# _dedup_latest_revision
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("rows, expected", [
    ([], {}),
    ([("G1-PROV", 1), ("G2-PROV", 7), ("G1-PROV", 9), ("G1-PROV", 4)], {"G1-PROV": 9, "G2-PROV": 7}),
])
def test_dedup_latest_revision_keeps_max_revision_per_concept(rows, expected):
    result = _dedup_latest_revision(rows)
    assert dict(result) == expected
    assert len(result) == len(expected)


# ---------------------------------------------------------------------------
# _run — main scan loop
# ---------------------------------------------------------------------------

class TestRunLoop:

    @pytest.mark.parametrize("start_id", [0, 777])
    def test_completes_when_probe_finds_nothing(self, deps, start_id):
        db, job_store, throttler = deps
        db.find_next_granule_id_in_range.return_value = None
        _run("req-1", "PROV", None, None, start_id)
        db.find_next_granule_id_in_range.assert_called_once_with("PROV", start_id, None)
        db.fetch_granule_id_range_chunk.assert_not_called()
        job_store.mark_job.assert_called_once_with("req-1", "completed")

    def test_window_with_data_advances_without_probing(self, deps, monkeypatch):
        """After a window with data, the next window starts at its end_id with no probe."""
        db, job_store, throttler = deps
        monkeypatch.setattr(_scanner_mod.config, "id_range_chunk_size", 1000)
        db.find_next_granule_id_in_range.side_effect = [500, None]
        db.fetch_granule_id_range_chunk.side_effect = [[("G1-PROV", 1)], [("G2-PROV", 1)], []]
        _run("req-1", "PROV", None, None, 0)
        assert [c.args[1:3] for c in db.fetch_granule_id_range_chunk.call_args_list] == [
            (500, 1500), (1500, 2500), (2500, 3500),
        ]
        assert [c.args[1] for c in db.find_next_granule_id_in_range.call_args_list] == [0, 3500]
        assert job_store.update_id_range_progress.call_args_list == [
            call("req-1", 1500), call("req-1", 2500), call("req-1", 3500),
        ]
        assert throttler.dispatch_in_batches.call_args_list[0] == call([("G1-PROV", 1)], "req-1", on_progress=ANY)
        job_store.mark_job.assert_called_once_with("req-1", "completed")

    def test_empty_window_reprobes_from_window_end(self, deps, monkeypatch):
        db, job_store, throttler = deps
        monkeypatch.setattr(_scanner_mod.config, "id_range_chunk_size", 1000)
        db.find_next_granule_id_in_range.side_effect = [500, 2000, None]
        db.fetch_granule_id_range_chunk.return_value = []
        _run("req-1", "PROV", _AFTER, _BEFORE, 0)
        assert [c.args for c in db.find_next_granule_id_in_range.call_args_list] == [
            ("PROV", 0, _AFTER), ("PROV", 1500, _AFTER), ("PROV", 3000, _AFTER),
        ]
        assert db.fetch_granule_id_range_chunk.call_args_list[0].args == ("PROV", 500, 1500, _AFTER, _BEFORE)
        assert job_store.update_id_range_progress.call_args_list == [call("req-1", 1500), call("req-1", 3000)]
        throttler.dispatch_in_batches.assert_not_called()

    def test_dedup_applied_before_dispatch(self, deps):
        db, job_store, throttler = deps
        db.find_next_granule_id_in_range.side_effect = [1, None]
        db.fetch_granule_id_range_chunk.side_effect = [[("G1-PROV", 1), ("G1-PROV", 9), ("G2-PROV", 1)], []]
        _run("req-1", "PROV", None, None, 0)
        assert sorted(throttler.dispatch_in_batches.call_args.args[0]) == [("G1-PROV", 9), ("G2-PROV", 1)]
        job_store.update_dispatched.assert_called_once_with("req-1", 2)

    def test_update_dispatched_called_once_per_sub_batch_not_once_per_window(self, deps):
        db, job_store, throttler = deps
        db.find_next_granule_id_in_range.side_effect = [1, None]
        db.fetch_granule_id_range_chunk.side_effect = [[("G%d-PROV" % i, 1) for i in range(5)], []]

        def _sub_batched_dispatch(records, request_id, on_progress=None):
            on_progress(3)
            on_progress(2)
            return True

        throttler.dispatch_in_batches.side_effect = _sub_batched_dispatch
        _run("req-1", "PROV", None, None, 0)
        assert [c.args[1] for c in job_store.update_dispatched.call_args_list] == [3, 2]

    # ------------------------------------------------------------------
    # Cancellation / shutdown
    # ------------------------------------------------------------------

    @pytest.mark.parametrize("stopped", [False, True])
    def test_cancelled_or_stopped_before_first_iteration_does_not_query_db(self, deps, stopped):
        db, job_store, throttler = deps
        if stopped:
            throttler.stop_event.set()
        else:
            throttler.is_job_cancelled.return_value = True
        _run("req-1", "PROV", None, None, 0)
        db.find_next_granule_id_in_range.assert_not_called()
        job_store.mark_job.assert_not_called()

    def test_cancelled_mid_chunk_dispatch_stops_without_completing(self, deps):
        db, job_store, throttler = deps
        db.find_next_granule_id_in_range.side_effect = [1, None]
        db.fetch_granule_id_range_chunk.return_value = [("G1-PROV", 1)]
        throttler.dispatch_in_batches.return_value = False
        _run("req-1", "PROV", None, None, 0)
        job_store.mark_job.assert_not_called()
        job_store.update_id_range_progress.assert_not_called()

    def test_cancelled_between_empty_windows_stops_before_next_probe(self, deps):
        db, job_store, throttler = deps
        db.find_next_granule_id_in_range.side_effect = [1, 50000, None]
        db.fetch_granule_id_range_chunk.return_value = []
        throttler.is_job_cancelled.side_effect = [False, True]
        _run("req-1", "PROV", None, None, 0)
        assert db.find_next_granule_id_in_range.call_count == 1
        job_store.mark_job.assert_not_called()

    def test_waits_for_green_before_probing(self, deps):
        db, job_store, throttler = deps
        _scanner_mod.check_all_es_health.side_effect = [{"overall": "red"}, {"overall": "green"}]
        db.find_next_granule_id_in_range.return_value = None
        _run("req-1", "PROV", None, None, 0)
        throttler.stop_event.wait.assert_called_once()
        job_store.mark_job.assert_called_once_with("req-1", "completed")

    # ------------------------------------------------------------------
    # Error handling
    # ------------------------------------------------------------------

    def test_db_error_marks_job_failed_not_completed(self, deps):
        db, job_store, throttler = deps
        db.find_next_granule_id_in_range.return_value = 1
        db.fetch_granule_id_range_chunk.side_effect = RuntimeError("boom")
        _run("req-1", "PROV", None, None, 0)
        assert [c.args[1] for c in job_store.mark_job.call_args_list] == ["failed"]

    def test_job_store_failure_in_except_block_does_not_propagate(self, deps):
        db, job_store, throttler = deps
        db.find_next_granule_id_in_range.side_effect = RuntimeError("ORA-03113")
        job_store.mark_job.side_effect = RuntimeError("DynamoDB unreachable")
        _run("req-1", "PROV", None, None, 0)  # must not raise


# ---------------------------------------------------------------------------
# start_id_range_scan
# ---------------------------------------------------------------------------

class TestStartIdRangeScan:

    def test_returns_started_daemon_thread(self, deps, monkeypatch):
        monkeypatch.setattr(_scanner_mod, "_run", MagicMock())
        thread = start_id_range_scan("req-1", "PROV", None, None)
        assert isinstance(thread, threading.Thread)
        assert thread.daemon is True
        thread.join(timeout=2)
        assert not thread.is_alive()

    def test_args_forwarded_with_default_start_id_zero(self, deps, monkeypatch):
        run_mock = MagicMock()
        monkeypatch.setattr(_scanner_mod, "_run", run_mock)
        thread = start_id_range_scan("req-1", "PROV", _AFTER, _BEFORE)
        thread.join(timeout=2)
        run_mock.assert_called_once_with("req-1", "PROV", _AFTER, _BEFORE, 0)


    def test_holds_lease_until_run_releases_it(self, deps, monkeypatch):
        """Held from the start, so a scan still waiting for a slot isn't restarted elsewhere."""
        monkeypatch.setattr(_scanner_mod, "_run", MagicMock())  # never releases
        start_id_range_scan("req-1", "PROV", None, None).join(timeout=2)
        assert _scanner_mod.leases.held_jobs() == {"req-1"}

# ---------------------------------------------------------------------------
# _wait_for_green_or_signal — ES-health gate
# ---------------------------------------------------------------------------

class TestWaitForGreenOrSignal:

    def test_returns_true_immediately_when_green(self, deps):
        db, job_store, throttler = deps
        assert _wait_for_green_or_signal("req-1") is True
        throttler.stop_event.wait.assert_not_called()

    @pytest.mark.parametrize("stopped", [False, True])
    def test_returns_false_without_checking_health_when_already_cancelled_or_stopped(self, deps, stopped):
        db, job_store, throttler = deps
        if stopped:
            throttler.stop_event.set()
        else:
            throttler.is_job_cancelled.return_value = True
        assert _wait_for_green_or_signal("req-1") is False
        _scanner_mod.check_all_es_health.assert_not_called()

    def test_loops_until_green(self, deps):
        db, job_store, throttler = deps
        _scanner_mod.check_all_es_health.side_effect = [
            {"overall": "red"}, {"overall": "yellow"}, {"overall": "green"},
        ]
        assert _wait_for_green_or_signal("req-1") is True
        assert throttler.stop_event.wait.call_args_list == [call(10.0), call(10.0)]

    def test_returns_false_when_cancelled_while_waiting(self, deps):
        db, job_store, throttler = deps
        _scanner_mod.check_all_es_health.return_value = {"overall": "red"}

        def _cancel_during_wait(*args, **kwargs):
            throttler.is_job_cancelled.return_value = True

        throttler.stop_event.wait.side_effect = _cancel_during_wait
        assert _wait_for_green_or_signal("req-1") is False


# ---------------------------------------------------------------------------
# _acquire_scan_slot / scan-thread concurrency cap
# ---------------------------------------------------------------------------

class TestAcquireScanSlot:
    """Mocks the semaphore so tests neither wait on nor leak real slots."""

    @pytest.mark.parametrize("acquire_results", [[True], [False, False, True]])
    def test_returns_true_once_a_slot_is_free(self, deps, monkeypatch, acquire_results):
        monkeypatch.setattr(_scanner_mod._scan_slots, "acquire", MagicMock(side_effect=acquire_results))
        assert _acquire_scan_slot("req-1") is True

    @pytest.mark.parametrize("stopped", [False, True])
    def test_returns_false_when_cancelled_or_stopped_while_waiting(self, deps, monkeypatch, stopped):
        db, job_store, throttler = deps
        monkeypatch.setattr(_scanner_mod._scan_slots, "acquire", MagicMock(return_value=False))
        if stopped:
            throttler.stop_event.set()
        else:
            throttler.is_job_cancelled.return_value = True
        assert _acquire_scan_slot("req-1") is False


class TestScanSlotLifecycle:

    def test_run_acquires_and_releases_slot_and_lease(self, deps, monkeypatch):
        db, job_store, throttler = deps
        db.find_next_granule_id_in_range.return_value = None
        acquire_mock = MagicMock(return_value=True)
        release_mock = MagicMock()
        monkeypatch.setattr(_scanner_mod, "_acquire_scan_slot", acquire_mock)
        monkeypatch.setattr(_scanner_mod._scan_slots, "release", release_mock)
        _scanner_mod.leases.hold("req-1")
        _run("req-1", "PROV", None, None, 0)
        acquire_mock.assert_called_once_with("req-1")
        release_mock.assert_called_once()
        assert _scanner_mod.leases.held_jobs() == set()

    def test_run_releases_slot_even_on_exception(self, deps, monkeypatch):
        db, job_store, throttler = deps
        db.find_next_granule_id_in_range.side_effect = RuntimeError("boom")
        release_mock = MagicMock()
        monkeypatch.setattr(_scanner_mod, "_acquire_scan_slot", MagicMock(return_value=True))
        monkeypatch.setattr(_scanner_mod._scan_slots, "release", release_mock)
        _run("req-1", "PROV", None, None, 0)
        release_mock.assert_called_once()

    def test_run_neither_queries_nor_releases_when_slot_unavailable(self, deps, monkeypatch):
        """Releasing a never-acquired slot would silently raise the concurrency cap."""
        db, job_store, throttler = deps
        release_mock = MagicMock()
        monkeypatch.setattr(_scanner_mod, "_acquire_scan_slot", MagicMock(return_value=False))
        monkeypatch.setattr(_scanner_mod._scan_slots, "release", release_mock)
        _run("req-1", "PROV", None, None, 0)
        db.find_next_granule_id_in_range.assert_not_called()
        release_mock.assert_not_called()
