"""
Unit tests for restart_lapsed_jobs() and the lease keeper's _tick().

job_store, the reindex router helpers, and the throttler are mocks, and restart
threads run inline — no real DynamoDB, SQS, or threads.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_lease_keeper.py -v
"""
import threading
from unittest.mock import MagicMock

import pytest

import app.lease_keeper as _keeper_mod
from app import leases
from app.lease_keeper import _tick, restart_lapsed_jobs


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

@pytest.fixture(autouse=True)
def deps(monkeypatch):
    reindex = MagicMock(ROUTE_TO_INTERNAL_TYPE={"variables": "variable"})
    throttler = MagicMock(current_job_id=None)
    monkeypatch.setattr(_keeper_mod, "reindex", reindex)
    monkeypatch.setattr(_keeper_mod, "throttler", throttler)
    monkeypatch.setattr(_keeper_mod, "log_slow_calls", MagicMock())
    monkeypatch.setattr(_keeper_mod, "_start_thread", lambda target, *args, **kwargs: target(*args, **kwargs))
    monkeypatch.setattr(leases, "_held", set())
    return reindex, throttler


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
        reindex, _ = deps
        leases.hold("job-1")
        js = _job_store(_job("granules-by-provider", provider_id="P"))
        restart_lapsed_jobs(js)
        js.claim_lapsed_job.assert_not_called()
        reindex.enqueue_provider.assert_not_called()

    def test_claim_lost_skips_job(self, deps):
        reindex, _ = deps
        js = _job_store(_job("granules-by-provider", provider_id="P"))
        js.claim_lapsed_job.return_value = False
        restart_lapsed_jobs(js)
        reindex.enqueue_provider.assert_not_called()

    @pytest.mark.parametrize("concept_type", ["granules", "granules-by-providers"])
    def test_enqueue_loop_restarted_skipping_enqueued_providers(self, deps, concept_type):
        reindex, _ = deps
        restart_lapsed_jobs(_job_store(_job(concept_type, providers_requested=["P1", "P2"], providers_enqueued=["P1"])))
        reindex.enqueue_providers.assert_called_once_with(
            "job-1", ["P1", "P2"], "A", "B", skip={"P1"}, include_deleted=False,
        )

    def test_all_providers_job_that_never_listed_providers_lists_them(self, deps):
        reindex, _ = deps
        restart_lapsed_jobs(_job_store(_job("granules")))
        reindex.enqueue_all_providers.assert_called_once_with("job-1", "A", "B", False)

    @pytest.mark.parametrize("persisted, start_id", [({"next_start_id": 123456}, 123456), ({}, 0)])
    def test_provider_scan_restarted_from_persisted_cursor(self, deps, persisted, start_id):
        reindex, _ = deps
        restart_lapsed_jobs(_job_store(_job("granules-by-provider", status="dispatching", provider_id="P", **persisted)))
        reindex.enqueue_provider.assert_called_once_with(
            "job-1", "P", "A", "B", start_id=start_id, include_deleted=False,
        )

    def test_concept_type_republished(self, deps):
        reindex, _ = deps
        restart_lapsed_jobs(_job_store(_job("variables")))
        reindex.publish_concept_type.assert_called_once_with("job-1", "variable", "B")

    @pytest.mark.parametrize("job", [
        _job("concept"), _job("granules-by-collection"), _job("granules-by-providers"),
    ], ids=["concept", "granules-by-collection", "granules-by-providers-without-list"])
    def test_unrestartable_job_marked_failed(self, job):
        js = _job_store(job)
        restart_lapsed_jobs(js)
        js.mark_job.assert_called_once_with("job-1", "failed")

    def test_restart_error_marks_job_failed(self, deps):
        reindex, _ = deps
        reindex.enqueue_provider.side_effect = RuntimeError("boom")
        js = _job_store(_job("granules-by-provider", provider_id="P"))
        restart_lapsed_jobs(js)
        js.mark_job.assert_called_once_with("job-1", "failed")


# ---------------------------------------------------------------------------
# _tick
# ---------------------------------------------------------------------------

class TestTick:

    def test_renews_message_and_every_held_job_then_restarts(self, deps):
        _, throttler = deps
        throttler.current_job_id = "collection-job"
        leases.hold("scan-job")
        js = _job_store()
        _tick(js, threading.Event())
        throttler.renew_message_lease.assert_called_once()
        assert sorted(c.args[0] for c in js.update_heartbeat.call_args_list) == ["collection-job", "scan-job"]
        _keeper_mod.log_slow_calls.assert_called_once_with(_keeper_mod._SLOW_CALL_SECONDS)
        js.find_lapsed_jobs.assert_called_once_with(_keeper_mod.config.lease_minutes)

    def test_shutting_down_keeps_renewing_but_restarts_nothing(self, deps):
        _, throttler = deps
        leases.hold("scan-job")
        js = _job_store()
        stop = threading.Event()
        stop.set()
        _tick(js, stop)
        throttler.renew_message_lease.assert_called_once()
        js.update_heartbeat.assert_called_once_with("scan-job")
        js.find_lapsed_jobs.assert_not_called()

    def test_failing_step_does_not_skip_the_rest(self, deps):
        _, throttler = deps
        throttler.renew_message_lease.side_effect = RuntimeError("SQS down")
        leases.hold("job-a")
        leases.hold("job-b")
        js = _job_store()
        js.update_heartbeat.side_effect = RuntimeError("DynamoDB down")
        _tick(js, threading.Event())
        assert js.update_heartbeat.call_count == 2
        js.find_lapsed_jobs.assert_called_once()
