"""
Unit tests for GET /health, GET /status, and the ES health utility.

/health must return 200 with zero dependency checks (ALB probe requirement).
/status is unauthenticated and returns live ES health + intermediate queue depth.
  - If SQS is unreachable, queue_depth is -1 (not a 5xx).
ES health utility: correct status aggregation across two clusters, red fallback
  on connection failure, and wait_for_green polling behaviour.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_health_status.py -v
"""
from unittest.mock import MagicMock, patch

import pytest
from fastapi.testclient import TestClient

from app.es.health import check_all_es_health, check_es_health, wait_for_green
from app.main import app

client = TestClient(app, raise_server_exceptions=False)


# ---------------------------------------------------------------------------
# GET /health — zero dependency checks
# ---------------------------------------------------------------------------

class TestHealth:

    def test_returns_200(self):
        r = client.get("/reindexer/health")
        assert r.status_code == 200

    def test_status_is_ok(self):
        assert client.get("/reindexer/health").json()["status"] == "ok"

    def test_service_name_present(self):
        assert "service" in client.get("/reindexer/health").json()

    def test_version_present(self):
        assert "version" in client.get("/reindexer/health").json()

    def test_requires_no_auth(self):
        r = client.get("/reindexer/health")
        assert r.status_code == 200

    def test_makes_no_external_calls(self):
        """ALB uses /health as its probe — it must never block on ES or SQS."""
        with patch("app.es.health.httpx.get", side_effect=AssertionError("ES called")):
            with patch("app.sqs.client._sqs", side_effect=AssertionError("SQS called")):
                r = client.get("/reindexer/health")
        assert r.status_code == 200


# ---------------------------------------------------------------------------
# GET /status — live ES health + queue depth, no auth
# ---------------------------------------------------------------------------

def _es_resp(status: str) -> MagicMock:
    m = MagicMock()
    m.json.return_value = {"status": status}
    return m


class TestStatus:

    def test_returns_200(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=0):
                r = client.get("/reindexer/status")
        assert r.status_code == 200

    def test_requires_no_auth(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=0):
                r = client.get("/reindexer/status")
        assert r.status_code == 200

    def test_response_contains_es_health(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=0):
                body = client.get("/reindexer/status").json()
        assert "es_health" in body

    def test_response_contains_page_queue_depth(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=42):
                body = client.get("/reindexer/status").json()
        assert body["page_queue_depth"] == 42

    def test_response_contains_collection_queue_depth(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=7):
                body = client.get("/reindexer/status").json()
        assert body["collection_queue_depth"] == 7

    def test_page_sqs_unreachable_returns_minus_one_not_5xx(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", side_effect=Exception("no sqs")):
                r = client.get("/reindexer/status")
        assert r.status_code == 200
        assert r.json()["page_queue_depth"] == -1

    def test_collection_sqs_unreachable_returns_minus_one_not_5xx(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", side_effect=Exception("no sqs")):
                r = client.get("/reindexer/status")
        assert r.status_code == 200
        assert r.json()["collection_queue_depth"] == -1

    def test_es_red_still_returns_200(self):
        with patch("app.es.health.httpx.get", side_effect=Exception("es down")):
            with patch("app.routers.status.get_queue_depth", return_value=0):
                r = client.get("/reindexer/status")
        assert r.status_code == 200

    def test_response_contains_throttler_alive(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=0):
                body = client.get("/reindexer/status").json()
        assert "throttler_alive" in body

    def test_response_contains_throttler_last_active(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=0):
                body = client.get("/reindexer/status").json()
        assert "throttler_last_active" in body

    def test_throttler_alive_false_when_not_started(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=0):
                body = client.get("/reindexer/status").json()
        assert body["throttler_alive"] is False

    def test_throttler_last_active_none_when_not_started(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=0):
                body = client.get("/reindexer/status").json()
        assert body["throttler_last_active"] is None

    def test_response_contains_rate_per_minute(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=0):
                body = client.get("/reindexer/status").json()
        assert "rate_per_minute" in body

    def test_response_contains_tokens_available(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=0):
                body = client.get("/reindexer/status").json()
        assert "tokens_available" in body

    def test_response_contains_indexer_queue_depth(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", return_value=5):
                body = client.get("/reindexer/status").json()
        assert body["indexer_queue_depth"] == 5

    def test_indexer_sqs_unreachable_returns_minus_one(self):
        with patch("app.es.health.httpx.get", return_value=_es_resp("green")):
            with patch("app.routers.status.get_queue_depth", side_effect=Exception("no sqs")):
                body = client.get("/reindexer/status").json()
        assert body["indexer_queue_depth"] == -1


# ---------------------------------------------------------------------------
# check_es_health — single cluster
# ---------------------------------------------------------------------------

class TestCheckEsHealth:

    def _resp(self, status):
        m = MagicMock()
        m.json.return_value = {"status": status}
        return m

    def test_returns_green(self):
        with patch("app.es.health.httpx.get", return_value=self._resp("green")):
            assert check_es_health("localhost", 9211) == "green"

    def test_returns_yellow(self):
        with patch("app.es.health.httpx.get", return_value=self._resp("yellow")):
            assert check_es_health("localhost", 9211) == "yellow"

    def test_returns_red(self):
        with patch("app.es.health.httpx.get", return_value=self._resp("red")):
            assert check_es_health("localhost", 9211) == "red"

    def test_returns_red_on_connection_error(self):
        with patch("app.es.health.httpx.get", side_effect=Exception("refused")):
            assert check_es_health("localhost", 9211) == "red"

    def test_returns_red_when_status_field_absent(self):
        m = MagicMock()
        m.json.return_value = {}
        with patch("app.es.health.httpx.get", return_value=m):
            assert check_es_health("localhost", 9211) == "red"


# ---------------------------------------------------------------------------
# check_all_es_health — two-cluster aggregation
# ---------------------------------------------------------------------------

class TestCheckAllEsHealth:

    def _patch(self, col_status, gran_status):
        resps = [_es_resp(col_status), _es_resp(gran_status)]
        return patch("app.es.health.httpx.get", side_effect=resps)

    def test_both_green_overall_green(self):
        with self._patch("green", "green"):
            assert check_all_es_health()["overall"] == "green"

    def test_one_yellow_overall_yellow(self):
        with self._patch("green", "yellow"):
            assert check_all_es_health()["overall"] == "yellow"

    def test_one_red_overall_red(self):
        with self._patch("green", "red"):
            assert check_all_es_health()["overall"] == "red"

    def test_both_red_overall_red(self):
        with self._patch("red", "red"):
            assert check_all_es_health()["overall"] == "red"

    def test_yellow_worse_than_green(self):
        with self._patch("yellow", "green"):
            assert check_all_es_health()["overall"] == "yellow"

    def test_response_has_collections_and_granules_keys(self):
        with self._patch("green", "green"):
            h = check_all_es_health()
        assert "collections" in h and "granules" in h

    def test_individual_statuses_reported_independently(self):
        with self._patch("yellow", "red"):
            h = check_all_es_health()
        assert h["collections"] == "yellow"
        assert h["granules"] == "red"


# ---------------------------------------------------------------------------
# wait_for_green — polling and timeout
# ---------------------------------------------------------------------------

class TestWaitForGreen:

    def test_returns_immediately_when_green(self):
        with patch("app.es.health.check_all_es_health", return_value={"overall": "green"}):
            wait_for_green(poll_interval_seconds=0.0, timeout_seconds=5.0)

    def test_polls_past_yellow_until_green(self):
        states = [{"overall": "yellow"}, {"overall": "yellow"}, {"overall": "green"}]
        with patch("app.es.health.check_all_es_health", side_effect=states):
            with patch("app.es.health.time.sleep"):
                wait_for_green(poll_interval_seconds=0.0, timeout_seconds=60.0)

    def test_polls_past_red_until_green(self):
        states = [{"overall": "red"}, {"overall": "red"}, {"overall": "green"}]
        with patch("app.es.health.check_all_es_health", side_effect=states):
            with patch("app.es.health.time.sleep"):
                wait_for_green(poll_interval_seconds=0.0, timeout_seconds=60.0)

    def test_raises_timeout_error_when_never_green(self):
        # negative timeout guarantees the deadline is already past on entry
        with patch("app.es.health.check_all_es_health", return_value={"overall": "yellow"}):
            with pytest.raises(TimeoutError):
                wait_for_green(poll_interval_seconds=0.0, timeout_seconds=-1.0)

    def test_returns_without_exception_when_stop_event_fires(self):
        import threading
        stop = threading.Event()
        stop.set()
        with patch("app.es.health.check_all_es_health", return_value={"overall": "yellow"}):
            wait_for_green(poll_interval_seconds=0.0, timeout_seconds=60.0, stop_event=stop)

    def test_stop_event_wakes_wait_immediately(self):
        import threading, time
        stop = threading.Event()
        t = threading.Timer(0.05, stop.set)
        t.start()
        with patch("app.es.health.check_all_es_health", return_value={"overall": "yellow"}):
            start = time.monotonic()
            wait_for_green(poll_interval_seconds=60.0, timeout_seconds=300.0, stop_event=stop)
        assert time.monotonic() - start < 2.0
