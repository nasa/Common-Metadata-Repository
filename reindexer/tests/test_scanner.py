"""Granule scans, with every dependency mocked and _run() called inline."""
import threading
from unittest.mock import MagicMock, call

import pytest

import app.throttler.scanner as _scanner_mod
from app.throttler.scanner import (
    _acquire_scan_slot,
    _dedup_latest_revision,
    _run,
    _wait_for_green_or_signal,
    start_scan,
)

_AFTER, _BEFORE = "2024-01-01T00:00:00Z", "2024-06-01T00:00:00Z"


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def deps(monkeypatch):
    db = MagicMock()
    job_store = MagicMock()
    throttler = MagicMock()
    throttler.is_job_cancelled.return_value = False
    throttler.stop_event = threading.Event()
    throttler.stop_event.wait = MagicMock()  # never actually sleep between ES polls
    throttler.dispatch_in_batches.return_value = True

    def _simulate_dispatch(records, request_id, on_progress=None):
        """Reports the whole chunk as one sub-batch."""
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


def _provider_job(job_store, providers=("PROV",), after=None, before=None, **cursor):
    job_store.get_job.return_value = {
        "job_id": "req-1", "concept_type": "granules-by-providers",
        "providers_requested": list(providers), "after": after, "before": before, **cursor,
    }


def _collection_job(job_store, **cursor):
    job_store.get_job.return_value = {
        "job_id": "req-1", "concept_type": "granules-by-collection", "collection_id": "C1-PROV", **cursor,
    }


# ---------------------------------------------------------------------------
# _dedup_latest_revision
# ---------------------------------------------------------------------------

def test_dedup_keeps_the_max_revision_row_per_concept():
    rows = [("G1-PROV", 1, 0), ("G2-PROV", 7, 0), ("G1-PROV", 9, 1), ("G1-PROV", 4, 0)]
    assert sorted(_dedup_latest_revision(rows)) == [("G1-PROV", 9, 1), ("G2-PROV", 7, 0)]


# ---------------------------------------------------------------------------
# Provider jobs — id-window scan
# ---------------------------------------------------------------------------

class TestProviderScan:

    @pytest.mark.parametrize("cursor, start_id", [({}, 0), ({"scan_provider": "PROV", "scan_cursor": 777}, 777)])
    def test_completes_when_probe_finds_nothing(self, deps, cursor, start_id):
        db, job_store, throttler = deps
        _provider_job(job_store, **cursor)
        db.find_next_granule_id_in_range.return_value = None
        _run("req-1")
        db.find_next_granule_id_in_range.assert_called_once_with("PROV", start_id, None)
        db.fetch_granule_id_range_chunk.assert_not_called()
        job_store.finish_provider.assert_called_once_with("req-1", "PROV")
        job_store.mark_job.assert_called_once_with("req-1", "completed")

    def test_window_with_data_advances_without_probing(self, deps, monkeypatch):
        """After a window with data, the next window starts at its end_id with no probe."""
        db, job_store, throttler = deps
        _provider_job(job_store)
        monkeypatch.setattr(_scanner_mod.config, "id_range_chunk_size", 1000)
        db.find_next_granule_id_in_range.side_effect = [500, None]
        db.fetch_granule_id_range_chunk.side_effect = [[("G1-PROV", 1, 0), ("G1-PROV", 2, 0)], [("G2-PROV", 1, 1)], []]
        _run("req-1")
        assert [c.args[1:3] for c in db.fetch_granule_id_range_chunk.call_args_list] == [
            (500, 1500), (1500, 2500), (2500, 3500),
        ]
        assert [c.args[1] for c in db.find_next_granule_id_in_range.call_args_list] == [0, 3500]
        assert job_store.update_scan_cursor.call_args_list == [
            call("req-1", 1500, provider_id="PROV"), call("req-1", 2500, provider_id="PROV"),
            call("req-1", 3500, provider_id="PROV"),
        ]
        # Latest revision per concept only; tombstones are dispatched with their deleted flag.
        assert [c.args[0] for c in throttler.dispatch_in_batches.call_args_list] == [
            [("G1-PROV", 2, 0)], [("G2-PROV", 1, 1)],
        ]
        job_store.mark_job.assert_called_once_with("req-1", "completed")

    def test_empty_window_reprobes_from_window_end(self, deps, monkeypatch):
        db, job_store, throttler = deps
        _provider_job(job_store, after=_AFTER, before=_BEFORE)
        monkeypatch.setattr(_scanner_mod.config, "id_range_chunk_size", 1000)
        db.find_next_granule_id_in_range.side_effect = [500, 2000, None]
        db.fetch_granule_id_range_chunk.return_value = []
        _run("req-1")
        assert [c.args for c in db.find_next_granule_id_in_range.call_args_list] == [
            ("PROV", 0, _AFTER), ("PROV", 1500, _AFTER), ("PROV", 3000, _AFTER),
        ]
        assert db.fetch_granule_id_range_chunk.call_args_list[0].args == ("PROV", 500, 1500, _AFTER, _BEFORE)
        throttler.dispatch_in_batches.assert_not_called()

    def test_update_dispatched_called_once_per_sub_batch_not_once_per_window(self, deps):
        db, job_store, throttler = deps
        _provider_job(job_store)
        db.find_next_granule_id_in_range.side_effect = [1, None]
        db.fetch_granule_id_range_chunk.side_effect = [[("G%d-PROV" % i, 1, 0) for i in range(5)], []]

        def _sub_batched_dispatch(records, request_id, on_progress=None):
            on_progress(3)
            on_progress(2)
            return True

        throttler.dispatch_in_batches.side_effect = _sub_batched_dispatch
        _run("req-1")
        assert [c.args[1] for c in job_store.update_dispatched.call_args_list] == [3, 2]

    def test_providers_scanned_in_order_skipping_done_and_resuming_the_cursor(self, deps):
        """The cursor only applies to the provider it was saved for."""
        db, job_store, throttler = deps
        _provider_job(
            job_store, providers=("A", "B", "C"),
            providers_done=["A"], scan_provider="B", scan_cursor=900,
        )
        db.find_next_granule_id_in_range.return_value = None
        _run("req-1")
        assert [c.args[:2] for c in db.find_next_granule_id_in_range.call_args_list] == [("B", 900), ("C", 0)]
        assert job_store.finish_provider.call_args_list == [call("req-1", "B"), call("req-1", "C")]
        job_store.mark_job.assert_called_once_with("req-1", "completed")

    # ------------------------------------------------------------------
    # Cancellation / shutdown
    # ------------------------------------------------------------------

    @pytest.mark.parametrize("stopped", [False, True])
    def test_cancelled_or_stopped_before_first_iteration_does_not_query_db(self, deps, stopped):
        db, job_store, throttler = deps
        _provider_job(job_store)
        if stopped:
            throttler.stop_event.set()
        else:
            throttler.is_job_cancelled.return_value = True
        _run("req-1")
        db.find_next_granule_id_in_range.assert_not_called()
        job_store.mark_job.assert_not_called()

    def test_cancelled_mid_window_dispatch_stops_without_saving_or_finishing(self, deps):
        db, job_store, throttler = deps
        _provider_job(job_store)
        db.find_next_granule_id_in_range.side_effect = [1, None]
        db.fetch_granule_id_range_chunk.return_value = [("G1-PROV", 1, 0)]
        throttler.dispatch_in_batches.return_value = False
        _run("req-1")
        job_store.update_scan_cursor.assert_not_called()
        job_store.finish_provider.assert_not_called()
        job_store.mark_job.assert_not_called()

    # ------------------------------------------------------------------
    # Error handling
    # ------------------------------------------------------------------

    def test_db_error_marks_job_failed_not_completed(self, deps):
        db, job_store, throttler = deps
        _provider_job(job_store)
        db.find_next_granule_id_in_range.return_value = 1
        db.fetch_granule_id_range_chunk.side_effect = RuntimeError("boom")
        _run("req-1")
        assert [c.args[1] for c in job_store.mark_job.call_args_list] == ["failed"]

    def test_missing_job_does_nothing(self, deps):
        db, job_store, throttler = deps
        job_store.get_job.return_value = None
        _run("req-1")
        job_store.mark_job.assert_not_called()


# ---------------------------------------------------------------------------
# Collection jobs — per-collection paging
# ---------------------------------------------------------------------------

class TestCollectionScan:

    def test_pages_dispatched_with_cursor_saved_after_each(self, deps):
        db, job_store, throttler = deps
        _collection_job(job_store, scan_cursor="G5-PROV")
        db.stream_granule_ids_paged.return_value = iter([
            ("G7-PROV", [("G6-PROV", 1, 0), ("G7-PROV", 2, 1)]),
            ("G9-PROV", []),
        ])
        _run("req-1")
        assert db.stream_granule_ids_paged.call_args.kwargs["start_after_concept_id"] == "G5-PROV"
        assert job_store.update_scan_cursor.call_args_list == [call("req-1", "G7-PROV"), call("req-1", "G9-PROV")]
        job_store.mark_job.assert_called_once_with("req-1", "completed")

    @pytest.mark.parametrize("stop_at", ["dispatch", "es-gate"])
    def test_stop_before_saving_the_page(self, deps, stop_at):
        db, job_store, throttler = deps
        _collection_job(job_store)
        db.stream_granule_ids_paged.return_value = iter([("G7-PROV", [("G7-PROV", 1, 0)])])
        if stop_at == "dispatch":
            throttler.dispatch_in_batches.return_value = False
        else:
            throttler.is_job_cancelled.return_value = True
        _run("req-1")
        assert throttler.dispatch_in_batches.called is (stop_at == "dispatch")
        job_store.update_scan_cursor.assert_not_called()
        job_store.mark_job.assert_not_called()


# ---------------------------------------------------------------------------
# start_scan
# ---------------------------------------------------------------------------

class TestStartScan:

    def test_runs_the_job_on_a_daemon_thread_holding_its_lease(self, deps, monkeypatch):
        """Held from the start, so a job still waiting for a slot isn't restarted elsewhere."""
        run_mock = MagicMock()  # stands in for _run, so the lease is never released
        monkeypatch.setattr(_scanner_mod, "_run", run_mock)
        thread = start_scan("req-1")
        thread.join(timeout=2)
        assert thread.daemon is True
        run_mock.assert_called_once_with("req-1")
        assert _scanner_mod.leases.held_jobs() == {"req-1"}

    def test_failed_thread_start_releases_the_lease(self, deps, monkeypatch):
        monkeypatch.setattr(_scanner_mod.threading.Thread, "start", MagicMock(side_effect=RuntimeError("can't start new thread")))
        with pytest.raises(RuntimeError):
            start_scan("req-1")
        assert _scanner_mod.leases.held_jobs() == set()


# ---------------------------------------------------------------------------
# _wait_for_green_or_signal — ES-health gate
# ---------------------------------------------------------------------------

class TestWaitForGreenOrSignal:

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

    def test_returns_true_once_a_slot_is_free(self, deps, monkeypatch):
        monkeypatch.setattr(_scanner_mod._scan_slots, "acquire", MagicMock(side_effect=[False, False, True]))
        assert _acquire_scan_slot("req-1") is True

    def test_returns_false_when_stopped_while_waiting(self, deps, monkeypatch):
        db, job_store, throttler = deps
        monkeypatch.setattr(_scanner_mod._scan_slots, "acquire", MagicMock(return_value=False))
        throttler.stop_event.set()
        assert _acquire_scan_slot("req-1") is False


class TestScanSlotLifecycle:

    def test_run_acquires_and_releases_slot_and_lease(self, deps, monkeypatch):
        db, job_store, throttler = deps
        _provider_job(job_store)
        db.find_next_granule_id_in_range.return_value = None
        acquire_mock = MagicMock(return_value=True)
        release_mock = MagicMock()
        monkeypatch.setattr(_scanner_mod, "_acquire_scan_slot", acquire_mock)
        monkeypatch.setattr(_scanner_mod._scan_slots, "release", release_mock)
        _scanner_mod.leases.hold("req-1")
        _run("req-1")
        acquire_mock.assert_called_once_with("req-1")
        release_mock.assert_called_once()
        assert _scanner_mod.leases.held_jobs() == set()

    def test_run_releases_slot_and_lease_even_when_marking_failed_fails(self, deps, monkeypatch):
        db, job_store, throttler = deps
        _provider_job(job_store)
        db.find_next_granule_id_in_range.side_effect = RuntimeError("ORA-03113")
        job_store.mark_job.side_effect = RuntimeError("DynamoDB unreachable")
        release_mock = MagicMock()
        monkeypatch.setattr(_scanner_mod, "_acquire_scan_slot", MagicMock(return_value=True))
        monkeypatch.setattr(_scanner_mod._scan_slots, "release", release_mock)
        _scanner_mod.leases.hold("req-1")
        _run("req-1")
        release_mock.assert_called_once()
        assert _scanner_mod.leases.held_jobs() == set()

    def test_run_neither_queries_nor_releases_when_slot_unavailable(self, deps, monkeypatch):
        """Releasing a never-acquired slot would silently raise the concurrency cap."""
        db, job_store, throttler = deps
        release_mock = MagicMock()
        monkeypatch.setattr(_scanner_mod, "_acquire_scan_slot", MagicMock(return_value=False))
        monkeypatch.setattr(_scanner_mod._scan_slots, "release", release_mock)
        _scanner_mod.leases.hold("req-1")
        _run("req-1")
        job_store.get_job.assert_not_called()
        release_mock.assert_not_called()
        assert _scanner_mod.leases.held_jobs() == set()
