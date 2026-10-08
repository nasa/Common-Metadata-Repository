"""restart_lapsed_jobs() and _tick(), with dependencies mocked and restart threads run inline."""
import threading
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

import app.lease_keeper as _keeper_mod
from app import leases
from app.lease_keeper import _tick, restart_lapsed_jobs


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

class _InlineThread:
    def __init__(self, target, args=(), **_):
        self._run = lambda: target(*args)

    def start(self):
        self._run()


@pytest.fixture(autouse=True)
def deps(monkeypatch):
    reindex = MagicMock(ROUTE_TO_INTERNAL_TYPE={"variables": "variable"})
    start_scan = MagicMock()
    monkeypatch.setattr(_keeper_mod, "reindex", reindex)
    monkeypatch.setattr(_keeper_mod, "start_scan", start_scan)
    monkeypatch.setattr(_keeper_mod, "log_slow_calls", MagicMock())
    monkeypatch.setattr(_keeper_mod, "threading", SimpleNamespace(Thread=_InlineThread, Event=threading.Event))
    monkeypatch.setattr(leases, "_held", set())
    return reindex, start_scan


def _job_store(*jobs):
    js = MagicMock()
    js.find_lapsed_jobs.return_value = list(jobs)
    js.claim_lapsed_job.return_value = True
    return js


def _job(concept_type, **kwargs):
    return {"job_id": "job-1", "status": "running", "last_heartbeat": "2026-08-24T00:00:00Z",
            "concept_type": concept_type, "after": "A", "before": "B", **kwargs}


# ---------------------------------------------------------------------------
# restart_lapsed_jobs
# ---------------------------------------------------------------------------

class TestRestartLapsedJobs:

    def test_job_held_by_this_task_is_left_alone(self, deps):
        _, start_scan = deps
        leases.hold("job-1")
        js = _job_store(_job("granules-by-provider"))
        restart_lapsed_jobs(js)
        js.claim_lapsed_job.assert_not_called()
        start_scan.assert_not_called()

    def test_claim_lost_skips_job(self, deps):
        _, start_scan = deps
        js = _job_store(_job("granules-by-provider"))
        js.claim_lapsed_job.return_value = False
        restart_lapsed_jobs(js)
        start_scan.assert_not_called()

    @pytest.mark.parametrize("concept_type", ["granules", "granules-by-collection"])
    def test_granule_job_claimed_and_rescanned_from_its_cursor(self, deps, concept_type):
        _, start_scan = deps
        js = _job_store(_job(concept_type))
        restart_lapsed_jobs(js)
        js.claim_lapsed_job.assert_called_once_with("job-1", "2026-08-24T00:00:00Z")
        start_scan.assert_called_once_with("job-1")

    def test_concept_type_republished(self, deps):
        reindex, _ = deps
        restart_lapsed_jobs(_job_store(_job("variables")))
        reindex.publish_concept_type.assert_called_once_with("job-1", "variable", "B")

    def test_single_concept_job_marked_failed(self):
        js = _job_store(_job("concept"))
        restart_lapsed_jobs(js)
        js.mark_job.assert_called_once_with("job-1", "failed")

    def test_restart_error_marks_job_failed(self, deps):
        _, start_scan = deps
        start_scan.side_effect = RuntimeError("can't start new thread")
        js = _job_store(_job("granules-by-provider"))
        restart_lapsed_jobs(js)
        js.mark_job.assert_called_once_with("job-1", "failed")


# ---------------------------------------------------------------------------
# _tick
# ---------------------------------------------------------------------------

class TestTick:

    def test_renews_every_held_job_then_restarts(self):
        leases.hold("job-a")
        leases.hold("job-b")
        js = _job_store()
        _tick(js, threading.Event())
        assert sorted(c.args[0] for c in js.update_heartbeat.call_args_list) == ["job-a", "job-b"]
        js.find_lapsed_jobs.assert_called_once_with(_keeper_mod.config.lease_minutes)

    def test_shutting_down_keeps_renewing_but_restarts_nothing(self):
        leases.hold("job-a")
        js = _job_store()
        stop = threading.Event()
        stop.set()
        _tick(js, stop)
        js.update_heartbeat.assert_called_once_with("job-a")
        js.find_lapsed_jobs.assert_not_called()

    def test_failing_renewal_does_not_skip_the_rest(self):
        leases.hold("job-a")
        leases.hold("job-b")
        js = _job_store()
        js.update_heartbeat.side_effect = RuntimeError("DynamoDB down")
        _tick(js, threading.Event())
        assert js.update_heartbeat.call_count == 2
        js.find_lapsed_jobs.assert_called_once()
