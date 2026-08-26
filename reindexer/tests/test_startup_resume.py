"""
Unit tests for resume_stalled_jobs().

All dependencies (db_client, job_store, enqueue_fn) are passed in as mocks —
no real DynamoDB or SQS connections are made.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_startup_resume.py -v
"""
from unittest.mock import MagicMock, call

import pytest

from app.startup import resume_stalled_jobs


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_deps(stalled_jobs=None):
    db = MagicMock()
    db.get_collection_ids_for_provider.return_value = ["C1-P", "C2-P"]
    js = MagicMock()
    js.find_stalled_jobs.return_value = stalled_jobs or []
    js.claim_stalled_job.return_value = True
    enqueue = MagicMock()
    return db, js, enqueue


def _job(concept_type, **kwargs):
    base = {
        "job_id": "job-1",
        "last_heartbeat": "2026-08-24T00:00:00Z",
        "concept_type": concept_type,
    }
    return {**base, **kwargs}


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestResumeStalledJobs:

    def test_no_stalled_jobs_does_nothing(self):
        db, js, enqueue = _make_deps(stalled_jobs=[])
        resume_stalled_jobs(db, js, enqueue)
        js.mark_job.assert_not_called()
        enqueue.assert_not_called()

    def test_non_granule_job_marked_failed(self):
        db, js, enqueue = _make_deps(stalled_jobs=[_job("variables")])
        resume_stalled_jobs(db, js, enqueue)
        js.mark_job.assert_called_once_with("job-1", "failed")
        enqueue.assert_not_called()

    def test_all_non_granule_types_marked_failed(self):
        for concept_type in ("variables", "services", "tools", "collections", "citation"):
            db, js, enqueue = _make_deps(stalled_jobs=[_job(concept_type)])
            resume_stalled_jobs(db, js, enqueue)
            js.mark_job.assert_called_with("job-1", "failed")

    def test_granule_job_re_enqueues_remaining_providers(self):
        db, js, enqueue = _make_deps(stalled_jobs=[
            _job("granules",
                 providers_to_process=["PROV_A", "PROV_B"],
                 providers_enqueued=["PROV_A"])
        ])
        db.get_collection_ids_for_provider.return_value = ["C1-P"]
        resume_stalled_jobs(db, js, enqueue)
        # Only PROV_B is remaining
        db.get_collection_ids_for_provider.assert_called_once_with("PROV_B")
        enqueue.assert_called_once_with(
            request_id="job-1", collection_id="C1-P", after=None, before=None
        )

    def test_granule_job_skips_already_enqueued_providers(self):
        db, js, enqueue = _make_deps(stalled_jobs=[
            _job("granules",
                 providers_to_process=["PROV_A"],
                 providers_enqueued=["PROV_A"])
        ])
        resume_stalled_jobs(db, js, enqueue)
        enqueue.assert_not_called()

    def test_granule_job_marked_running_after_resume(self):
        db, js, enqueue = _make_deps(stalled_jobs=[_job("granules")])
        resume_stalled_jobs(db, js, enqueue)
        js.mark_job.assert_called_with("job-1", "running")

    def test_claim_lost_skips_job(self):
        db, js, enqueue = _make_deps(stalled_jobs=[_job("granules")])
        js.claim_stalled_job.return_value = False
        resume_stalled_jobs(db, js, enqueue)
        js.mark_job.assert_not_called()
        enqueue.assert_not_called()

    def test_granules_by_provider_re_enqueues_provider(self):
        db, js, enqueue = _make_deps(stalled_jobs=[
            _job("granules-by-provider", provider_id="MYPROV")
        ])
        db.get_collection_ids_for_provider.return_value = ["C1-P"]
        resume_stalled_jobs(db, js, enqueue)
        db.get_collection_ids_for_provider.assert_called_once_with("MYPROV")
        enqueue.assert_called()

    def test_granules_by_collection_re_enqueues_collection(self):
        db, js, enqueue = _make_deps(stalled_jobs=[
            _job("granules-by-collection", collection_id="C42-PROV")
        ])
        resume_stalled_jobs(db, js, enqueue)
        enqueue.assert_called_once_with(
            request_id="job-1", collection_id="C42-PROV", after=None, before=None
        )

    def test_passes_date_params_when_present(self):
        db, js, enqueue = _make_deps(stalled_jobs=[
            _job("granules-by-collection",
                 collection_id="C1-P",
                 after="2024-01-01T00:00:00Z",
                 before="2024-12-31T23:59:59Z")
        ])
        resume_stalled_jobs(db, js, enqueue)
        kw = enqueue.call_args[1]
        assert kw["after"] == "2024-01-01T00:00:00Z"
        assert kw["before"] == "2024-12-31T23:59:59Z"
