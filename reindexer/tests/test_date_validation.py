"""
Unit tests for date parameter validation.

Covers _parse_utc_z format enforcement, _validate_date_params logic, and the
X-CMR-Override-Date-Limit header wiring through the FastAPI endpoints.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_date_validation.py -v
"""
from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock, patch

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.routers.reindex import _parse_utc_z, _validate_date_params


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _days_ago(n: float) -> str:
    """ISO8601 Z timestamp for n days before now, to the second."""
    dt = datetime.now(timezone.utc) - timedelta(days=n)
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def _assert_400(fn, *args, **kwargs):
    with pytest.raises(HTTPException) as exc_info:
        fn(*args, **kwargs)
    assert exc_info.value.status_code == 400
    return exc_info.value.detail


# ---------------------------------------------------------------------------
# _parse_utc_z — format enforcement
# ---------------------------------------------------------------------------

class TestParseUtcZ:
    def test_valid_z_timestamp_returns_aware_datetime(self):
        dt = _parse_utc_z("2024-06-15T12:30:00Z", "after")
        assert dt.year == 2024
        assert dt.month == 6
        assert dt.day == 15
        assert dt.tzinfo == timezone.utc

    def test_offset_suffix_rejected(self):
        detail = _assert_400(_parse_utc_z, "2024-01-01T00:00:00+00:00", "after")
        assert "after" in detail

    def test_date_only_rejected(self):
        _assert_400(_parse_utc_z, "2024-01-01", "after")

    def test_fractional_seconds_rejected(self):
        _assert_400(_parse_utc_z, "2024-01-01T00:00:00.000Z", "after")

    def test_missing_z_rejected(self):
        _assert_400(_parse_utc_z, "2024-01-01T00:00:00", "after")

    def test_non_padded_digits_rejected(self):
        _assert_400(_parse_utc_z, "2024-1-1T0:0:0Z", "after")

    def test_param_name_appears_in_error_detail(self):
        detail = _assert_400(_parse_utc_z, "bad", "before")
        assert "before" in detail


# ---------------------------------------------------------------------------
# _validate_date_params — logic
# ---------------------------------------------------------------------------

class TestValidateDateParams:

    # -- format errors -------------------------------------------------------

    def test_bad_after_format_raises_400(self):
        _assert_400(_validate_date_params, "2024-01-01", None, False)

    def test_bad_before_format_raises_400(self):
        _assert_400(_validate_date_params, None, "not-a-date", False)

    # -- ordering constraint -------------------------------------------------

    def test_after_equal_to_before_raises_400(self):
        ts = "2024-06-01T00:00:00Z"
        detail = _assert_400(_validate_date_params, ts, ts, False)
        assert "after" in detail.lower()

    def test_after_greater_than_before_raises_400(self):
        _assert_400(
            _validate_date_params,
            "2024-06-02T00:00:00Z",
            "2024-06-01T00:00:00Z",
            False,
        )

    def test_after_less_than_before_passes(self):
        _validate_date_params(_days_ago(2), _days_ago(1), False)

    # -- 30-day constraint ---------------------------------------------------

    def test_after_1_day_ago_passes_without_override(self):
        _validate_date_params(_days_ago(1), None, False)

    def test_after_29_days_ago_passes_without_override(self):
        _validate_date_params(_days_ago(29), None, False)

    def test_after_31_days_ago_raises_without_override(self):
        detail = _assert_400(_validate_date_params, _days_ago(31), None, False)
        assert "30 days" in detail
        assert "X-CMR-Override-Date-Limit" in detail

    def test_after_365_days_ago_raises_without_override(self):
        _assert_400(_validate_date_params, _days_ago(365), None, False)

    def test_after_31_days_ago_passes_with_override(self):
        _validate_date_params(_days_ago(31), None, True)

    def test_after_365_days_ago_passes_with_override(self):
        _validate_date_params(_days_ago(365), None, True)

    def test_after_31_days_ago_with_before_passes_with_override(self):
        _validate_date_params(_days_ago(31), _days_ago(20), True)

    # -- before-only is unconstrained ----------------------------------------

    def test_before_only_no_30_day_check(self):
        # before without after: no 30-day constraint applies
        _validate_date_params(None, "2020-01-01T00:00:00Z", False)

    # -- both absent passes --------------------------------------------------

    def test_no_dates_passes(self):
        _validate_date_params(None, None, False)
        _validate_date_params(None, None, True)


# ---------------------------------------------------------------------------
# Header wiring through FastAPI endpoints
# (patches require_auth to bypass auth; patches enqueue_collection_item to
#  avoid needing a real SQS endpoint)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def client():
    from app.auth import require_auth
    from app.main import app

    app.dependency_overrides[require_auth] = lambda: "test-token"
    yield TestClient(app, raise_server_exceptions=False)
    app.dependency_overrides.clear()


@pytest.fixture(autouse=True)
def mock_sqs(monkeypatch):
    monkeypatch.setattr("app.routers.reindex.enqueue_collection_item", MagicMock())
    monkeypatch.setattr("app.routers.reindex.job_store", MagicMock())


class TestOverrideHeaderWiring:

    def test_after_31_days_ago_without_header_returns_400(self, client):
        r = client.post(
            "/reindexer/reindex/granules",
            params={"after": _days_ago(31)},
        )
        assert r.status_code == 400
        assert "30 days" in r.json()["detail"]

    def test_after_31_days_ago_with_header_true_returns_202(self, client):
        r = client.post(
            "/reindexer/reindex/granules",
            params={"after": _days_ago(31)},
            headers={"X-CMR-Override-Date-Limit": "true"},
        )
        assert r.status_code == 202

    def test_override_header_true_uppercase_returns_202(self, client):
        r = client.post(
            "/reindexer/reindex/granules",
            params={"after": _days_ago(31)},
            headers={"X-CMR-Override-Date-Limit": "TRUE"},
        )
        assert r.status_code == 202

    def test_override_header_false_does_not_bypass(self, client):
        r = client.post(
            "/reindexer/reindex/granules",
            params={"after": _days_ago(31)},
            headers={"X-CMR-Override-Date-Limit": "false"},
        )
        assert r.status_code == 400

    def test_bad_after_format_returns_400(self, client):
        r = client.post(
            "/reindexer/reindex/granules",
            params={"after": "2024-01-01"},
        )
        assert r.status_code == 400

    def test_bad_before_format_returns_400(self, client):
        r = client.post(
            "/reindexer/reindex/granules",
            params={"before": "not-a-date"},
        )
        assert r.status_code == 400

    def test_after_ge_before_returns_400(self, client):
        r = client.post(
            "/reindexer/reindex/granules",
            params={
                "after": "2024-06-15T00:00:00Z",
                "before": "2024-06-14T00:00:00Z",
            },
        )
        assert r.status_code == 400

    def test_date_validation_applied_to_provider_endpoint(self, client):
        r = client.post(
            "/reindexer/reindex/granules/provider/MYPROV",
            params={"after": _days_ago(31)},
        )
        assert r.status_code == 400

    def test_date_validation_applied_to_collection_endpoint(self, client):
        r = client.post(
            "/reindexer/reindex/granules/collection/C1234-PROV",
            params={"after": _days_ago(31)},
        )
        assert r.status_code == 400

    def test_override_header_applied_to_provider_endpoint(self, client):
        r = client.post(
            "/reindexer/reindex/granules/provider/MYPROV",
            params={"after": _days_ago(31)},
            headers={"X-CMR-Override-Date-Limit": "true"},
        )
        assert r.status_code == 202

    def test_override_header_applied_to_collection_endpoint(self, client):
        r = client.post(
            "/reindexer/reindex/granules/collection/C1234-PROV",
            params={"after": _days_ago(31)},
            headers={"X-CMR-Override-Date-Limit": "true"},
        )
        assert r.status_code == 202
