"""restart_lapsed_jobs() and _renew_leases_and_restart_lapsed_jobs(), with dependencies mocked and restart threads run inline."""
import threading
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

import app.lease_keeper as _keeper_mod
from app import leases
from app.lease_keeper import _renew_leases_and_restart_lapsed_jobs, restart_lapsed_jobs


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
        """When a lapsed job is still held by this task, then it isn't claimed or restarted."""
        _, start_scan = deps
        leases.hold("job-1")
        js = _job_store(_job("granules-by-provider"))
        restart_lapsed_jobs(js)
        js.claim_lapsed_job.assert_not_called()
        start_scan.assert_not_called()

    def test_claim_lost_skips_job(self, deps):
        """When another task claims a lapsed job first, then this task doesn't restart it."""
        _, start_scan = deps
        js = _job_store(_job("granules-by-provider"))
        js.claim_lapsed_job.return_value = False
        restart_lapsed_jobs(js)
        start_scan.assert_not_called()

    @pytest.mark.parametrize("concept_type", ["granules", "granules-by-collection"])
    def test_granule_job_claimed_and_rescanned_from_its_cursor(self, deps, concept_type):
        """When a granule job lapses, then it's claimed with the heartbeat seen and its scan
        restarted."""
        _, start_scan = deps
        js = _job_store(_job(concept_type))
        restart_lapsed_jobs(js)
        js.claim_lapsed_job.assert_called_once_with("job-1", "2026-08-24T00:00:00Z")
        start_scan.assert_called_once_with("job-1")

    def test_concept_type_republished(self, deps):
        """When a concept-type job lapses, then it's republished from the start."""
        reindex, _ = deps
        restart_lapsed_jobs(_job_store(_job("variables")))
        reindex.publish_concept_type.assert_called_once_with("job-1", "variable", "B")

    def test_single_concept_job_marked_failed(self):
        """When a single-concept job lapses, then it's marked failed, since it can't be restarted."""
        js = _job_store(_job("concept"))
        restart_lapsed_jobs(js)
        js.mark_job.assert_called_once_with("job-1", "failed")

    def test_restart_error_marks_job_failed(self, deps):
        """When restarting a lapsed job raises, then the job is marked failed."""
        _, start_scan = deps
        start_scan.side_effect = RuntimeError("can't start new thread")
        js = _job_store(_job("granules-by-provider"))
        restart_lapsed_jobs(js)
        js.mark_job.assert_called_once_with("job-1", "failed")


# ---------------------------------------------------------------------------
# _renew_leases_and_restart_lapsed_jobs
# ---------------------------------------------------------------------------

class TestTick:

    def test_renews_every_held_job_then_restarts(self):
        """When the keeper ticks, then it renews every held job's heartbeat, then looks for lapsed
        jobs."""
        leases.hold("job-a")
        leases.hold("job-b")
        js = _job_store()
        _renew_leases_and_restart_lapsed_jobs(js, threading.Event())
        assert sorted(c.args[0] for c in js.update_heartbeat.call_args_list) == ["job-a", "job-b"]
        js.find_lapsed_jobs.assert_called_once_with(_keeper_mod.config.lease_minutes)

    def test_shutting_down_keeps_renewing_but_restarts_nothing(self):
        """When the task is shutting down, then held jobs are still renewed but nothing is
        restarted."""
        leases.hold("job-a")
        js = _job_store()
        stop = threading.Event()
        stop.set()
        _renew_leases_and_restart_lapsed_jobs(js, stop)
        js.update_heartbeat.assert_called_once_with("job-a")
        js.find_lapsed_jobs.assert_not_called()

    def test_failing_renewal_does_not_skip_the_rest(self):
        """When one renewal fails, then the other jobs are still renewed and lapsed jobs still
        checked."""
        leases.hold("job-a")
        leases.hold("job-b")
        js = _job_store()
        js.update_heartbeat.side_effect = RuntimeError("DynamoDB down")
        _renew_leases_and_restart_lapsed_jobs(js, threading.Event())
        assert js.update_heartbeat.call_count == 2
        js.find_lapsed_jobs.assert_called_once()
