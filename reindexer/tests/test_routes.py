"""
Unit tests for the /reindex/* and /jobs endpoints. Auth is bypassed via
dependency_overrides; see test_auth.py for auth.
"""
from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient

import app.routers.reindex as _r
import app.routers.status as _s


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
    monkeypatch.setattr(_r, "publish_concept_update", MagicMock())
    monkeypatch.setattr(_r, "publish_indexer_events_batch", MagicMock())
    monkeypatch.setattr(_r, "check_all_es_health", MagicMock(return_value={"overall": "green"}))
    monkeypatch.setattr(_r, "start_scan", MagicMock())
    monkeypatch.setattr(_r, "throttler", MagicMock(**{"is_job_cancelled.return_value": False}))
    monkeypatch.setattr(_r.leases, "_held", set())

    mock_db = MagicMock()
    mock_db.stream_concept_ids_by_type.return_value = []
    mock_db.get_concept_by_id.return_value = None
    mock_db.get_all_provider_ids.return_value = ["PROV_A", "PROV_B", "PROV_C"]
    mock_db.is_small_provider.return_value = False
    monkeypatch.setattr(_r, "db_client", mock_db)

    mock_job_store = MagicMock()
    mock_job_store.get_job.return_value = None
    mock_job_store.try_cancel_job.return_value = True
    monkeypatch.setattr(_r, "job_store", mock_job_store)
    monkeypatch.setattr(_s, "job_store", mock_job_store)


def _created():
    """(job_id, concept_type, kwargs) of the create_job call."""
    job_id, concept_type = _r.job_store.create_job.call_args.args
    return job_id, concept_type, _r.job_store.create_job.call_args.kwargs


def _statuses():
    return [c.args[1] for c in _r.job_store.mark_job.call_args_list]


def _ts(**delta) -> str:
    return (datetime.now(timezone.utc) - timedelta(**delta)).strftime("%Y-%m-%dT%H:%M:%SZ")


_ISO_RE = __import__("re").compile(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$')


# ---------------------------------------------------------------------------
# POST /reindex/{concept_type}
# ---------------------------------------------------------------------------

class TestConceptTypeEndpoint:

    def test_concept_type_job(self, client):
        r = client.post("/reindexer/reindex/data-quality-summaries")
        assert r.status_code == 202
        job_id, concept_type, kw = _created()
        assert (job_id, concept_type) == (r.json()["request_id"], "data-quality-summaries")
        assert _ISO_RE.match(kw["before"])
        _r.db_client.stream_concept_ids_by_type.assert_called_once_with("data-quality-summary", before=kw["before"])
        assert _statuses() == ["completed"]

    def test_unknown_type_returns_404(self, client):
        r = client.post("/reindexer/reindex/badtype")
        assert r.status_code == 404
        assert "badtype" in r.json()["detail"]

    @pytest.mark.parametrize("n, sends", [(0, []), (750, [500, 250]), (1000, [500, 500])])
    def test_sends_in_batches_counting_each(self, client, n, sends):
        _r.db_client.stream_concept_ids_by_type.return_value = [(f"V{i}-PROV", i) for i in range(n)]
        client.post("/reindexer/reindex/variables")
        assert [len(c.args[0]) for c in _r.publish_indexer_events_batch.call_args_list] == sends
        assert [c.args[1] for c in _r.job_store.update_dispatched.call_args_list] == sends

    @pytest.mark.parametrize("n, published", [(10, False), (500, True)], ids=["before-first-send", "after-streaming"])
    def test_cancel_stops_without_completing(self, n, published):
        _r.db_client.stream_concept_ids_by_type.return_value = [(f"V{i}-PROV", i) for i in range(n)]
        if published:
            # Cancelled only after the full batch went out.
            calls = []
            _r.throttler.is_job_cancelled.side_effect = lambda job_id: calls.append(1) or len(calls) > 1
        else:
            _r.throttler.is_job_cancelled.return_value = True
        _r.publish_concept_type("job-1", "variable")
        assert _r.publish_indexer_events_batch.called is published
        assert _statuses() == []

    def test_stream_error_marks_job_failed(self):
        _r.db_client.stream_concept_ids_by_type.side_effect = RuntimeError("ORA-03113")
        _r.publish_concept_type("job-1", "variable")
        assert _statuses() == ["failed"]

    def test_es_not_green_fails_without_sending(self, client):
        _r.check_all_es_health.return_value = {"overall": "red"}
        assert client.post("/reindexer/reindex/variables").status_code == 202
        assert _statuses() == ["failed"]
        _r.publish_indexer_events_batch.assert_not_called()


# ---------------------------------------------------------------------------
# POST /reindex/concept/{concept_id}
# ---------------------------------------------------------------------------

class TestConceptEndpoint:

    def test_found_concept_is_published(self, client):
        _r.db_client.get_concept_by_id.return_value = {"concept-id": "V1234-PROV", "revision-id": 7}
        r = client.post("/reindexer/reindex/concept/V1234-PROV")
        job_id = _created()[0]
        assert (r.status_code, r.json()["request_id"]) == (202, job_id)
        _r.publish_concept_update.assert_called_once_with("V1234-PROV", 7, job_id)
        _r.job_store.update_dispatched.assert_called_once_with(job_id, 1)
        assert _statuses() == ["completed"]

    def test_missing_concept_returns_404_and_fails_the_job(self, client):
        assert client.post("/reindexer/reindex/concept/V9999-MISSING").status_code == 404
        _r.publish_concept_update.assert_not_called()
        assert _statuses() == ["failed"]

    @pytest.mark.parametrize("concept_id, status", [
        ("TL99-PROV", 202), ("VIS1-PROV_01", 202), ("not-a-concept-id", 400),
    ])
    def test_concept_id_format(self, client, concept_id, status):
        _r.db_client.get_concept_by_id.return_value = {"concept-id": concept_id, "revision-id": 1}
        assert client.post(f"/reindexer/reindex/concept/{concept_id}").status_code == status
        assert _r.db_client.get_concept_by_id.called is (status == 202)

    def test_publish_error_returns_500_and_fails_the_job(self, client):
        _r.db_client.get_concept_by_id.return_value = {"concept-id": "V1-P", "revision-id": 1}
        _r.publish_concept_update.side_effect = RuntimeError("SQS down")
        assert client.post("/reindexer/reindex/concept/V1-P").status_code == 500
        assert _statuses() == ["failed"]

    def test_es_not_green_returns_503_without_publishing(self, client):
        _r.db_client.get_concept_by_id.return_value = {"concept-id": "V1-P", "revision-id": 1}
        _r.check_all_es_health.return_value = {"overall": "red"}
        assert client.post("/reindexer/reindex/concept/V1-P").status_code == 503
        _r.publish_concept_update.assert_not_called()
        assert _statuses() == ["failed"]


# ---------------------------------------------------------------------------
# Leases on work done outside a scan
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("kind", ["concept-type", "concept"])
def test_lease_held_while_working_and_released_after(client, kind):
    """Without it, the lease keeper wouldn't renew the job and another task would redo it."""
    held = []
    record = lambda *a, **kw: held.append(_r.job_store.create_job.call_args.args[0] in _r.leases.held_jobs())  # noqa: E731
    if kind == "concept-type":
        _r.db_client.stream_concept_ids_by_type.side_effect = lambda *a, **kw: record() or iter([])
        client.post("/reindexer/reindex/variables")
    else:
        _r.db_client.get_concept_by_id.side_effect = lambda *a: record() or {"concept-id": "V1-P", "revision-id": 1}
        client.post("/reindexer/reindex/concept/V1-P")
    assert held == [True]
    assert _r.leases.held_jobs() == set()


def test_source_url_includes_the_query(client):
    client.post("/reindexer/reindex/granules/provider/PROV_A?before=2024-06-01T00:00:00Z")
    assert _created()[2]["source_url"] == "/reindexer/reindex/granules/provider/PROV_A?before=2024-06-01T00:00:00Z"


# ---------------------------------------------------------------------------
# Granule endpoints: each creates one job and starts its scan
# ---------------------------------------------------------------------------

class TestGranuleEndpoints:

    def test_all_granules_scans_every_provider(self, client):
        r = client.post("/reindexer/reindex/granules")
        assert r.status_code == 202
        job_id, concept_type, kw = _created()
        assert (job_id, concept_type) == (r.json()["request_id"], "granules")
        assert kw["providers"] == ["PROV_A", "PROV_B", "PROV_C"]
        _r.start_scan.assert_called_once_with(job_id)

    def test_all_granules_provider_lookup_failure_returns_503_without_a_job(self, client):
        _r.db_client.get_all_provider_ids.side_effect = Exception("ORA-12541")
        assert client.post("/reindexer/reindex/granules").status_code == 503
        _r.job_store.create_job.assert_not_called()

    def test_by_provider(self, client):
        r = client.post("/reindexer/reindex/granules/provider/PROV_A")
        assert r.status_code == 202
        job_id, concept_type, kw = _created()
        assert (concept_type, kw["providers"]) == ("granules-by-provider", ["PROV_A"])
        _r.start_scan.assert_called_once_with(job_id)

    def test_invalid_provider_id_rejected_before_any_db_call(self, client):
        assert client.post("/reindexer/reindex/granules/provider/bad.provider!").status_code == 400
        _r.db_client.get_all_provider_ids.assert_not_called()
        _r.start_scan.assert_not_called()

    def test_unknown_provider_returns_400_without_creating_job(self, client):
        assert client.post("/reindexer/reindex/granules/provider/NOT_A_PROV").status_code == 400
        _r.job_store.create_job.assert_not_called()

    def test_by_collection(self, client):
        r = client.post("/reindexer/reindex/granules/collection/C1234567890-MYPROV")
        assert r.status_code == 202
        job_id, concept_type, kw = _created()
        assert (concept_type, kw["collection_id"]) == ("granules-by-collection", "C1234567890-MYPROV")
        _r.start_scan.assert_called_once_with(job_id)

    @pytest.mark.parametrize("collection_id", ["c1-prov", "C1-PROV'--"])
    def test_invalid_collection_id_rejected(self, client, collection_id):
        """collection_id is embedded in SQL as a literal, so this check is the guard."""
        assert client.post(f"/reindexer/reindex/granules/collection/{collection_id}").status_code == 400
        _r.db_client.is_small_provider.assert_not_called()
        _r.job_store.create_job.assert_not_called()

    def test_explicit_before_is_kept_and_a_missing_one_is_now(self, client):
        client.post("/reindexer/reindex/granules/provider/PROV_A?before=2024-06-01T00:00:00Z")
        assert _created()[2]["before"] == "2024-06-01T00:00:00Z"
        client.post("/reindexer/reindex/granules/provider/PROV_A")
        assert _ISO_RE.match(_created()[2]["before"])


class TestProviderListEndpoint:

    def test_creates_one_job_for_the_providers_deduped(self, client):
        r = client.post("/reindexer/reindex/granules/providers", json={"provider_ids": ["PROV_B", "PROV_A", "PROV_B"]})
        assert r.status_code == 202
        job_id, concept_type, kw = _created()
        assert (concept_type, kw["providers"]) == ("granules-by-providers", ["PROV_B", "PROV_A"])
        _r.start_scan.assert_called_once_with(job_id)

    def test_empty_list_returns_400(self, client):
        assert client.post("/reindexer/reindex/granules/providers", json={"provider_ids": []}).status_code == 400

    def test_invalid_provider_id_rejected_before_any_db_call(self, client):
        r = client.post("/reindexer/reindex/granules/providers", json={"provider_ids": ["PROV_A", "bad.provider!"]})
        assert r.status_code == 400
        _r.db_client.get_all_provider_ids.assert_not_called()

    def test_one_unknown_provider_rejects_whole_request(self, client):
        r = client.post(
            "/reindexer/reindex/granules/providers",
            json={"provider_ids": ["PROV_A", "NOT_A_REAL_PROVIDER", "PROV_B"]},
        )
        assert r.status_code == 400
        assert "NOT_A_REAL_PROVIDER" in r.json()["detail"]
        _r.job_store.create_job.assert_not_called()
        _r.start_scan.assert_not_called()

    def test_provider_existence_check_failure_returns_503_without_a_job(self, client):
        _r.db_client.get_all_provider_ids.side_effect = Exception("ORA-12541: no listener")
        r = client.post("/reindexer/reindex/granules/providers", json={"provider_ids": ["PROV_A"]})
        assert r.status_code == 503
        _r.job_store.create_job.assert_not_called()

    def test_bad_date_rejected_before_provider_existence_check(self, client):
        r = client.post("/reindexer/reindex/granules/providers?after=not-a-date", json={"provider_ids": ["PROV_A"]})
        assert r.status_code == 400
        _r.db_client.get_all_provider_ids.assert_not_called()


class TestSmallProvidersDisabled:

    @pytest.fixture(autouse=True)
    def prov_b_is_small(self):
        _r.db_client.is_small_provider.side_effect = lambda p: p == "PROV_B"

    @pytest.mark.parametrize("path, body", [
        ("/reindexer/reindex/granules/provider/PROV_B", None),
        ("/reindexer/reindex/granules/providers", {"provider_ids": ["PROV_A", "PROV_B"]}),
        ("/reindexer/reindex/granules/collection/C1-PROV_B", None),
        ("/reindexer/reindex/concept/G1-PROV_B", None),
    ])
    def test_rejected_without_creating_a_job(self, client, path, body):
        assert client.post(path, json=body).status_code == 400
        _r.job_store.create_job.assert_not_called()

    def test_grids_are_not_granules(self, client):
        _r.db_client.get_concept_by_id.return_value = {"concept-id": "GRD1-PROV_B", "revision-id": 1}
        assert client.post("/reindexer/reindex/concept/GRD1-PROV_B").status_code == 202

    def test_all_providers_run_skips_them(self, client):
        client.post("/reindexer/reindex/granules")
        assert _created()[2]["providers"] == ["PROV_A", "PROV_C"]


# ---------------------------------------------------------------------------
# GET /jobs, GET /jobs/{job_id}, DELETE /jobs/{job_id}
# ---------------------------------------------------------------------------

class TestJobsEndpoints:

    def test_list_forwards_filter_and_limit(self, client):
        _s.job_store.list_jobs.return_value = [{"job_id": "j1", "status": "running"}]
        body = client.get("/reindexer/jobs?status=running&limit=10").json()
        assert [j["job_id"] for j in body["jobs"]] == ["j1"]
        _s.job_store.list_jobs.assert_called_once_with(status_filter="running", limit=10)

    def test_get_returns_the_enriched_job(self, client):
        started = _ts(seconds=60)
        _s.job_store.get_job.return_value = {
            "job_id": "abc-123", "status": "running", "started_at": started, "last_heartbeat": started,
            "total_dispatched": 1000,
        }
        body = client.get("/reindexer/jobs/abc-123").json()
        _s.job_store.get_job.assert_called_once_with("abc-123")
        assert (body["job_id"], body["status"]) == ("abc-123", "running")
        assert {"elapsed_seconds", "heartbeat_age_seconds", "lease_lapsed", "avg_dispatch_rate_per_minute"} <= body.keys()

    def test_get_missing_returns_404(self, client):
        assert client.get("/reindexer/jobs/nonexistent").status_code == 404

    def test_delete_cancels(self, client):
        _s.job_store.get_job.return_value = {"job_id": "del-job", "status": "running"}
        r = client.delete("/reindexer/jobs/del-job")
        assert (r.status_code, r.json()) == (200, {"job_id": "del-job", "status": "cancelled"})
        _s.job_store.try_cancel_job.assert_called_once_with("del-job")

    def test_delete_missing_returns_404(self, client):
        assert client.delete("/reindexer/jobs/missing").status_code == 404

    def test_delete_finished_returns_409(self, client):
        _s.job_store.get_job.return_value = {"job_id": "done-job", "status": "completed"}
        _s.job_store.try_cancel_job.return_value = False
        assert client.delete("/reindexer/jobs/done-job").status_code == 409


# ---------------------------------------------------------------------------
# _enrich_job — computed fields
# ---------------------------------------------------------------------------

class TestJobEnrichment:

    def _enrich(self, job: dict) -> dict:
        from app.routers.status import _enrich_job
        return _enrich_job(job)

    def test_elapsed_and_rate_for_a_running_job(self):
        result = self._enrich({"job_id": "j1", "status": "running", "started_at": _ts(seconds=60), "total_dispatched": 6000})
        assert 58 <= result["elapsed_seconds"] <= 62
        assert 5800 <= result["avg_dispatch_rate_per_minute"] <= 6200

    def test_finished_job_measured_to_completed_at_so_rate_does_not_decay(self):
        started = datetime.now(timezone.utc) - timedelta(hours=2)
        result = self._enrich({
            "job_id": "j1", "status": "completed",
            "started_at": started.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "completed_at": (started + timedelta(seconds=60)).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "total_dispatched": 6000,
        })
        assert (result["elapsed_seconds"], result["avg_dispatch_rate_per_minute"]) == (60, 6000)

    @pytest.mark.parametrize("minutes_ago, lapsed", [(0, False), (25, True)])
    def test_heartbeat_age_and_lease_lapsed(self, minutes_ago, lapsed):
        result = self._enrich({"job_id": "j1", "status": "running", "last_heartbeat": _ts(minutes=minutes_ago)})
        assert abs(result["heartbeat_age_seconds"] - minutes_ago * 60) <= 2
        assert result["lease_lapsed"] is lapsed

    def test_lease_lapsed_absent_for_stopped_jobs(self):
        result = self._enrich({"job_id": "j1", "status": "completed", "ttl": 123, "last_heartbeat": "2020-01-01T00:00:00Z"})
        assert "lease_lapsed" not in result
        assert "ttl" not in result

    def test_missing_fields_do_not_raise(self):
        result = self._enrich({"job_id": "j1", "status": "running", "total_dispatched": 1000})
        assert "elapsed_seconds" not in result
        assert "avg_dispatch_rate_per_minute" not in result

    def test_providers_remaining_lists_unfinished_providers_in_order(self):
        result = self._enrich({
            "job_id": "j1", "status": "cancelled",
            "providers_requested": ["PROV_C", "PROV_A", "PROV_B"],
            "providers_done": {"PROV_A"},
        })
        assert result["providers_remaining"] == ["PROV_C", "PROV_B"]
