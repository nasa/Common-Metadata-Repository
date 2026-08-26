"""
Unit tests for /reindex/* FastAPI endpoint routing.

Covers:
  - All 11 concept types return 202 with a request_id
  - Unknown concept type returns 404
  - /reindex/concept/{concept_id}: found → 202, not found → 404, bad format → 400
  - All known concept-id prefix formats pass validation

Auth is bypassed via dependency_overrides; see test_auth.py for full auth coverage.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_routes.py -v
"""
from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def client():
    from app.auth import require_auth
    from app.main import app

    app.dependency_overrides[require_auth] = lambda: "test-token"
    yield TestClient(app, raise_server_exceptions=False)
    app.dependency_overrides.clear()


@pytest.fixture(autouse=True)
def mock_deps(monkeypatch):
    """Replace all external I/O in the reindex/status routers with safe no-op mocks."""
    monkeypatch.setattr("app.routers.reindex.enqueue_collection_item", MagicMock())
    monkeypatch.setattr("app.routers.reindex.publish_concept_update", MagicMock())

    mock_db = MagicMock()
    mock_db.get_concept_ids_by_type.return_value = []
    mock_db.get_concept_by_id.return_value = None
    mock_db.get_all_provider_ids.return_value = []
    mock_db.get_collection_ids_for_provider.return_value = []
    monkeypatch.setattr("app.routers.reindex.db_client", mock_db)

    mock_job_store = MagicMock()
    mock_job_store.get_job.return_value = None
    mock_job_store.try_cancel_job.return_value = True
    monkeypatch.setattr("app.routers.reindex.job_store", mock_job_store)
    monkeypatch.setattr("app.routers.status.job_store", mock_job_store)


# ---------------------------------------------------------------------------
# POST /reindex/{concept_type}
# ---------------------------------------------------------------------------

_KNOWN_CONCEPT_TYPES = [
    "variables", "services", "tools", "collections",
    "generics", "data-quality-summaries", "order-options",
    "visualizations", "subscriptions", "grid", "citation",
]


class TestConceptTypeEndpoint:

    @pytest.mark.parametrize("concept_type", _KNOWN_CONCEPT_TYPES)
    def test_known_type_returns_202(self, client, concept_type):
        r = client.post(f"/reindexer/reindex/{concept_type}")
        assert r.status_code == 202, (
            f"{concept_type!r}: expected 202, got {r.status_code} — {r.text}"
        )

    @pytest.mark.parametrize("concept_type", _KNOWN_CONCEPT_TYPES)
    def test_known_type_response_has_uuid_request_id(self, client, concept_type):
        body = client.post(f"/reindexer/reindex/{concept_type}").json()
        assert "request_id" in body
        assert len(body["request_id"]) == 36  # UUID4 canonical form

    def test_unknown_type_returns_404(self, client):
        r = client.post("/reindexer/reindex/does-not-exist")
        assert r.status_code == 404

    def test_unknown_type_detail_mentions_type_name(self, client):
        r = client.post("/reindexer/reindex/badtype")
        assert "badtype" in r.json()["detail"]


# ---------------------------------------------------------------------------
# POST /reindex/concept/{concept_id}
# ---------------------------------------------------------------------------

class TestConceptEndpoint:

    def test_found_concept_returns_202(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_concept_by_id.return_value = {"concept-id": "V1234-PROV", "revision-id": 3}
        r = client.post("/reindexer/reindex/concept/V1234-PROV")
        assert r.status_code == 202

    def test_found_concept_publishes_update_with_correct_args(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_concept_by_id.return_value = {"concept-id": "V1234-PROV", "revision-id": 7}
        client.post("/reindexer/reindex/concept/V1234-PROV")
        _r.publish_concept_update.assert_called_once()
        args = _r.publish_concept_update.call_args.args
        assert args[0] == "V1234-PROV"
        assert args[1] == 7

    def test_not_found_returns_404(self, client):
        # autouse mock_deps default: get_concept_by_id returns None
        r = client.post("/reindexer/reindex/concept/V9999-MISSING")
        assert r.status_code == 404

    def test_not_found_does_not_publish(self, client):
        import app.routers.reindex as _r
        client.post("/reindexer/reindex/concept/V9999-MISSING")
        _r.publish_concept_update.assert_not_called()

    def test_invalid_format_returns_400(self, client):
        r = client.post("/reindexer/reindex/concept/not-a-concept-id")
        assert r.status_code == 400

    def test_invalid_format_detail_includes_bad_id(self, client):
        r = client.post("/reindexer/reindex/concept/BADFORMAT")
        assert "BADFORMAT" in r.json()["detail"]

    def test_invalid_format_does_not_touch_db(self, client):
        import app.routers.reindex as _r
        client.post("/reindexer/reindex/concept/lowercase-id")
        _r.db_client.get_concept_by_id.assert_not_called()

    @pytest.mark.parametrize("concept_id", [
        "C1234567890-MYPROV",
        "G1234567890-MYPROV",
        "V1234567890-MYPROV",
        "TL99-PROV",
        "SUB1-PROV",
        "DQS42-NASA",
        "OO1-PROV",
        "GRD1-PROV",
        "CIT1-PROV",
        "VIS1-PROV_01",
    ])
    def test_all_known_prefixes_pass_format_validation(self, client, concept_id):
        import app.routers.reindex as _r
        _r.db_client.get_concept_by_id.return_value = {"concept-id": concept_id, "revision-id": 1}
        r = client.post(f"/reindexer/reindex/concept/{concept_id}")
        assert r.status_code == 202, f"{concept_id!r}: expected 202, got {r.status_code}"


# ---------------------------------------------------------------------------
# Job tracking — create_job called on POST endpoints
# ---------------------------------------------------------------------------

class TestJobTracking:

    def test_reindex_concept_type_creates_job(self, client):
        import app.routers.reindex as _r
        client.post("/reindexer/reindex/variables")
        _r.job_store.create_job.assert_called_once()

    def test_reindex_concept_type_job_uses_route_segment_as_type(self, client):
        import app.routers.reindex as _r
        client.post("/reindexer/reindex/services")
        args = _r.job_store.create_job.call_args.args
        assert args[1] == "services"

    def test_reindex_granules_creates_job_with_granules_type(self, client):
        import app.routers.reindex as _r
        client.post("/reindexer/reindex/granules")
        args = _r.job_store.create_job.call_args.args
        assert args[1] == "granules"

    def test_reindex_concept_creates_job(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_concept_by_id.return_value = {"concept-id": "V1-P", "revision-id": 1}
        client.post("/reindexer/reindex/concept/V1-P")
        _r.job_store.create_job.assert_called_once()

    def test_reindex_concept_not_found_still_creates_job(self, client):
        import app.routers.reindex as _r
        # default mock: get_concept_by_id returns None
        client.post("/reindexer/reindex/concept/V9-MISSING")
        _r.job_store.create_job.assert_called_once()
        _r.job_store.mark_job.assert_called_once_with(
            _r.job_store.create_job.call_args.args[0], "failed"
        )

    def test_request_id_in_response_matches_job_id(self, client):
        import app.routers.reindex as _r
        r = client.post("/reindexer/reindex/variables")
        request_id = r.json()["request_id"]
        job_id_arg = _r.job_store.create_job.call_args.args[0]
        assert request_id == job_id_arg


# ---------------------------------------------------------------------------
# Granule job status transitions — background tasks must mark "dispatching"
# ---------------------------------------------------------------------------

class TestGranuleJobStatusTransitions:
    """Background tasks for granule endpoints mark the job 'dispatching' (not
    'completed') because enqueuing to the collection queue is not the same as
    the throttler finishing dispatch to CMR.
    """

    def test_reindex_all_granules_marks_dispatching(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_all_provider_ids.return_value = []
        r = client.post("/reindexer/reindex/granules")
        assert r.status_code == 202
        statuses = [c.args[1] for c in _r.job_store.mark_job.call_args_list]
        assert "dispatching" in statuses

    def test_reindex_all_granules_does_not_mark_completed(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_all_provider_ids.return_value = []
        r = client.post("/reindexer/reindex/granules")
        assert r.status_code == 202
        statuses = [c.args[1] for c in _r.job_store.mark_job.call_args_list]
        assert "completed" not in statuses

    def test_reindex_provider_granules_marks_dispatching(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_collection_ids_for_provider.return_value = []
        r = client.post("/reindexer/reindex/granules/provider/TESTPROV")
        assert r.status_code == 202
        statuses = [c.args[1] for c in _r.job_store.mark_job.call_args_list]
        assert "dispatching" in statuses

    def test_reindex_provider_granules_does_not_mark_completed(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_collection_ids_for_provider.return_value = []
        r = client.post("/reindexer/reindex/granules/provider/TESTPROV")
        assert r.status_code == 202
        statuses = [c.args[1] for c in _r.job_store.mark_job.call_args_list]
        assert "completed" not in statuses

    def test_reindex_collection_granules_marks_dispatching(self, client):
        import app.routers.reindex as _r
        r = client.post("/reindexer/reindex/granules/collection/C1234567890-MYPROV")
        assert r.status_code == 202
        statuses = [c.args[1] for c in _r.job_store.mark_job.call_args_list]
        assert "dispatching" in statuses

    def test_reindex_collection_granules_does_not_mark_completed(self, client):
        import app.routers.reindex as _r
        r = client.post("/reindexer/reindex/granules/collection/C1234567890-MYPROV")
        assert r.status_code == 202
        statuses = [c.args[1] for c in _r.job_store.mark_job.call_args_list]
        assert "completed" not in statuses

    def test_reindex_collection_granules_increments_work_items_before_dispatching(self, client):
        import app.routers.reindex as _r
        client.post("/reindexer/reindex/granules/collection/C1234567890-MYPROV")
        _r.job_store.update_progress.assert_called_once_with(
            _r.job_store.create_job.call_args.args[0], work_items_delta=1
        )

    def test_empty_provider_calls_try_complete_job_after_dispatching(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_collection_ids_for_provider.return_value = []
        client.post("/reindexer/reindex/granules/provider/EMPTYPROV")
        _r.job_store.try_complete_job.assert_called_once()

    def test_concept_type_reindex_still_marks_completed(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_concept_ids_by_type.return_value = []
        r = client.post("/reindexer/reindex/variables")
        assert r.status_code == 202
        statuses = [c.args[1] for c in _r.job_store.mark_job.call_args_list]
        assert "completed" in statuses


# ---------------------------------------------------------------------------
# GET /jobs/{job_id} and DELETE /jobs/{job_id}
# ---------------------------------------------------------------------------

class TestJobsEndpoint:

    def test_get_job_returns_200_when_found(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = {
            "job_id": "abc-123", "status": "running", "concept_type": "granules"
        }
        r = client.get("/reindexer/jobs/abc-123")
        assert r.status_code == 200

    def test_get_job_returns_job_body(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = {
            "job_id": "abc-123", "status": "completed", "concept_type": "variables"
        }
        body = client.get("/reindexer/jobs/abc-123").json()
        assert body["job_id"] == "abc-123"
        assert body["status"] == "completed"

    def test_get_job_returns_404_when_not_found(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = None
        r = client.get("/reindexer/jobs/nonexistent")
        assert r.status_code == 404

    def test_get_job_passes_job_id_to_store(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = {"job_id": "my-job", "status": "running"}
        client.get("/reindexer/jobs/my-job")
        _s.job_store.get_job.assert_called_once_with("my-job")

    def test_delete_job_returns_200_when_found(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = {"job_id": "del-job", "status": "running"}
        r = client.delete("/reindexer/jobs/del-job")
        assert r.status_code == 200

    def test_delete_job_calls_try_cancel_job(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = {"job_id": "del-job", "status": "running"}
        client.delete("/reindexer/jobs/del-job")
        _s.job_store.try_cancel_job.assert_called_once_with("del-job")

    def test_delete_job_returns_cancelled_status(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = {"job_id": "del-job", "status": "running"}
        body = client.delete("/reindexer/jobs/del-job").json()
        assert body["status"] == "cancelled"
        assert body["job_id"] == "del-job"

    def test_delete_job_returns_404_when_not_found(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = None
        r = client.delete("/reindexer/jobs/missing")
        assert r.status_code == 404

    def test_delete_job_returns_409_when_try_cancel_fails(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = {"job_id": "done-job", "status": "completed"}
        _s.job_store.try_cancel_job.return_value = False
        r = client.delete("/reindexer/jobs/done-job")
        assert r.status_code == 409

    def test_delete_job_does_not_call_mark_job(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = {"job_id": "done-job", "status": "completed"}
        _s.job_store.try_cancel_job.return_value = False
        client.delete("/reindexer/jobs/done-job")
        _s.job_store.mark_job.assert_not_called()

    def test_delete_job_allows_cancel_of_running_job(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = {"job_id": "active-job", "status": "running"}
        r = client.delete("/reindexer/jobs/active-job")
        assert r.status_code == 200

    def test_delete_job_allows_cancel_of_dispatching_job(self, client):
        import app.routers.status as _s
        _s.job_store.get_job.return_value = {"job_id": "active-job", "status": "dispatching"}
        r = client.delete("/reindexer/jobs/active-job")
        assert r.status_code == 200


# ---------------------------------------------------------------------------
# N1 — Snapshot end timestamp: granule endpoints always set an implicit before
# ---------------------------------------------------------------------------

class TestJobEnrichment:
    """Unit tests for _enrich_job computed fields."""

    def _enrich(self, job: dict) -> dict:
        from app.routers.status import _enrich_job
        return _enrich_job(job)

    def test_elapsed_seconds_computed_from_started_at(self):
        from datetime import datetime, timedelta, timezone
        started = (datetime.now(timezone.utc) - timedelta(seconds=120)).strftime("%Y-%m-%dT%H:%M:%SZ")
        result = self._enrich({"job_id": "j1", "status": "running", "started_at": started})
        assert 118 <= result["elapsed_seconds"] <= 122

    def test_heartbeat_age_seconds_computed(self):
        from datetime import datetime, timedelta, timezone
        hb = (datetime.now(timezone.utc) - timedelta(seconds=45)).strftime("%Y-%m-%dT%H:%M:%SZ")
        result = self._enrich({"job_id": "j1", "status": "running", "last_heartbeat": hb})
        assert 43 <= result["heartbeat_age_seconds"] <= 47

    def test_heartbeat_stale_false_when_recent(self):
        from datetime import datetime, timezone
        hb = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        result = self._enrich({"job_id": "j1", "status": "running", "last_heartbeat": hb})
        assert result["heartbeat_stale"] is False

    def test_heartbeat_stale_true_when_old(self):
        from datetime import datetime, timedelta, timezone
        hb = (datetime.now(timezone.utc) - timedelta(minutes=25)).strftime("%Y-%m-%dT%H:%M:%SZ")
        result = self._enrich({"job_id": "j1", "status": "running", "last_heartbeat": hb})
        assert result["heartbeat_stale"] is True

    def test_pct_complete_computed_when_both_counts_present(self):
        result = self._enrich({
            "job_id": "j1", "status": "dispatching",
            "total_dispatched": 500, "total_granules_expected": 1000,
        })
        assert result["pct_complete"] == 50.0

    def test_pct_complete_absent_when_expected_is_zero(self):
        result = self._enrich({
            "job_id": "j1", "status": "dispatching",
            "total_dispatched": 0, "total_granules_expected": 0,
        })
        assert "pct_complete" not in result

    def test_pct_complete_absent_when_expected_missing(self):
        result = self._enrich({"job_id": "j1", "status": "running"})
        assert "pct_complete" not in result

    def test_original_fields_preserved(self):
        result = self._enrich({"job_id": "j1", "status": "running", "concept_type": "granules"})
        assert result["job_id"] == "j1"
        assert result["status"] == "running"
        assert result["concept_type"] == "granules"

    def test_missing_timestamps_do_not_raise(self):
        result = self._enrich({"job_id": "j1", "status": "running"})
        assert result["job_id"] == "j1"

    def test_get_job_response_includes_enriched_fields(self, client):
        import app.routers.status as _s
        from datetime import datetime, timedelta, timezone
        started = (datetime.now(timezone.utc) - timedelta(seconds=60)).strftime("%Y-%m-%dT%H:%M:%SZ")
        _s.job_store.get_job.return_value = {
            "job_id": "j1", "status": "running",
            "started_at": started, "last_heartbeat": started,
            "total_dispatched": 250, "total_granules_expected": 1000,
        }
        body = client.get("/reindexer/jobs/j1").json()
        assert "elapsed_seconds" in body
        assert "heartbeat_age_seconds" in body
        assert "heartbeat_stale" in body
        assert body["pct_complete"] == 25.0


class TestListJobsEndpoint:

    def test_returns_200(self, client):
        import app.routers.status as _s
        _s.job_store.list_jobs.return_value = []
        r = client.get("/reindexer/jobs")
        assert r.status_code == 200

    def test_requires_no_auth(self, client):
        import app.routers.status as _s
        _s.job_store.list_jobs.return_value = []
        r = client.get("/reindexer/jobs")
        assert r.status_code == 200

    def test_returns_jobs_list(self, client):
        import app.routers.status as _s
        _s.job_store.list_jobs.return_value = [{"job_id": "j1", "status": "running"}]
        body = client.get("/reindexer/jobs").json()
        assert body["count"] == 1
        assert body["jobs"][0]["job_id"] == "j1"

    def test_status_filter_forwarded_to_store(self, client):
        import app.routers.status as _s
        _s.job_store.list_jobs.return_value = []
        client.get("/reindexer/jobs?status=running")
        _s.job_store.list_jobs.assert_called_with(status_filter="running", limit=50)

    def test_limit_param_forwarded_to_store(self, client):
        import app.routers.status as _s
        _s.job_store.list_jobs.return_value = []
        client.get("/reindexer/jobs?limit=10")
        _s.job_store.list_jobs.assert_called_with(status_filter=None, limit=10)

    def test_limit_capped_at_200(self, client):
        import app.routers.status as _s
        _s.job_store.list_jobs.return_value = []
        r = client.get("/reindexer/jobs?limit=999")
        assert r.status_code == 422

    def test_limit_minimum_is_1(self, client):
        import app.routers.status as _s
        _s.job_store.list_jobs.return_value = []
        r = client.get("/reindexer/jobs?limit=0")
        assert r.status_code == 422


class TestSnapshotBeforeTimestamp:
    """When no explicit before param is given, granule endpoints snapshot now as
    the implicit before bound to stabilise offset-based Oracle pagination."""

    _ISO_RE = __import__("re").compile(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$')

    def test_all_granules_sets_before_in_job(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_all_provider_ids.return_value = []
        client.post("/reindexer/reindex/granules")
        kw = _r.job_store.create_job.call_args[1]
        assert kw.get("before") is not None
        assert self._ISO_RE.match(kw["before"])

    def test_all_granules_preserves_explicit_before(self, client):
        import app.routers.reindex as _r
        explicit = "2024-06-01T00:00:00Z"
        client.post(f"/reindexer/reindex/granules?before={explicit}")
        kw = _r.job_store.create_job.call_args[1]
        assert kw.get("before") == explicit

    def test_by_provider_sets_before_in_job(self, client):
        import app.routers.reindex as _r
        client.post("/reindexer/reindex/granules/provider/TESTPROV")
        kw = _r.job_store.create_job.call_args[1]
        assert kw.get("before") is not None
        assert self._ISO_RE.match(kw["before"])

    def test_by_collection_sets_before_in_job(self, client):
        import app.routers.reindex as _r
        client.post("/reindexer/reindex/granules/collection/C1234567890-PROV")
        kw = _r.job_store.create_job.call_args[1]
        assert kw.get("before") is not None
        assert self._ISO_RE.match(kw["before"])

    def test_by_collection_before_forwarded_to_enqueue(self, client):
        import app.routers.reindex as _r
        client.post("/reindexer/reindex/granules/collection/C1234567890-PROV")
        kw = _r.enqueue_collection_item.call_args[1]
        assert kw.get("before") is not None
        assert self._ISO_RE.match(kw["before"])

    def test_concept_type_reindex_sets_before_in_job(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_concept_ids_by_type.return_value = []
        client.post("/reindexer/reindex/variables")
        kw = _r.job_store.create_job.call_args[1]
        assert kw.get("before") is not None
        assert self._ISO_RE.match(kw["before"])

    def test_concept_type_reindex_passes_before_to_db(self, client):
        import app.routers.reindex as _r
        _r.db_client.get_concept_ids_by_type.return_value = []
        client.post("/reindexer/reindex/variables")
        kw = _r.db_client.get_concept_ids_by_type.call_args[1]
        assert kw.get("before") is not None
        assert self._ISO_RE.match(kw["before"])
