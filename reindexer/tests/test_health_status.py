"""GET /health (an ALB probe, so no dependency checks), GET /status, and ES health."""
from unittest.mock import MagicMock, patch

import pytest
from fastapi.testclient import TestClient

from app.es.health import check_all_es_health, check_es_health
from app.main import app

client = TestClient(app, raise_server_exceptions=False)


def _es_resp(status: str) -> MagicMock:
    m = MagicMock()
    m.json.return_value = {"status": status}
    return m


def test_health_makes_no_external_calls():
    with patch("app.es.health.httpx.get", side_effect=AssertionError("ES called")):
        with patch("app.sqs.client._sqs", side_effect=AssertionError("SQS called")):
            r = client.get("/reindexer/health")
    assert (r.status_code, r.json()["status"]) == (200, "ok")


class TestStatus:

    @pytest.fixture(autouse=True)
    def no_jobs(self, monkeypatch):
        import app.leases as leases
        monkeypatch.setattr(leases, "_held", set())

    def _get(self, counts=None, side_effect=None):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_counts", return_value=counts, side_effect=side_effect):
                return client.get("/reindexer/status")

    def test_shape(self):
        import app.leases as leases
        leases.hold("job-b")
        leases.hold("job-a")
        counts = {"available": 3, "in_flight": 1}
        body = self._get(counts).json()
        assert body["es_health"]["overall"] == "green"
        assert body["indexer_queue"] == counts
        task = body["task"]
        assert task["id"]
        assert task["jobs_in_progress"] == ["job-a", "job-b"]
        assert task["rate_limit_per_minute"] > 0

    def test_sqs_unreachable_reports_null_counts_not_5xx(self):
        r = self._get(side_effect=Exception("no sqs"))
        assert r.status_code == 200
        assert r.json()["indexer_queue"] is None


@pytest.mark.parametrize("resp", [Exception("refused"), MagicMock(**{"json.return_value": {}}), _es_resp("purple")],
                         ids=["unreachable", "no-status-field", "unknown-status"])
def test_check_es_health_is_none_when_unknown(resp):
    kwargs = {"side_effect": resp} if isinstance(resp, Exception) else {"return_value": resp}
    with patch("app.es.health.httpx.get", **kwargs):
        assert check_es_health("localhost", 9211) is None


@pytest.mark.parametrize("col, gran, overall", [
    ("green", "green", "green"),
    ("green", "yellow", "yellow"),
    ("yellow", "green", "yellow"),
    ("yellow", "red", "red"),
    ("green", None, None),
])
def test_check_all_es_health_reports_the_worse_cluster(col, gran, overall):
    resps = [_es_resp(s) if s else Exception("unreachable") for s in (col, gran)]
    with patch("app.es.health.httpx.get", side_effect=resps):
        assert check_all_es_health() == {"collections": col, "granules": gran, "overall": overall}
