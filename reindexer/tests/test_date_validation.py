"""
Unit tests for date parameter validation and the X-CMR-Override-Date-Limit header wiring.
"""
from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.routers.reindex import _validate_date_params


def _days_ago(n: float) -> str:
    return (datetime.now(timezone.utc) - timedelta(days=n)).strftime("%Y-%m-%dT%H:%M:%SZ")


@pytest.mark.parametrize("after, before, override, ok", [
    (_days_ago(29), None, False, True),
    (_days_ago(31), None, True, True),
    (None, "2020-01-01T00:00:00Z", False, True),        # before alone has no age limit
    (_days_ago(31), None, False, False),
    ("2024-06-01T00:00:00Z", "2024-06-01T00:00:00Z", True, False),
    ("2024-06-02T00:00:00Z", "2024-06-01T00:00:00Z", True, False),
    ("2024-01-01T00:00:00+00:00", None, True, False),
    ("2024-01-01T00:00:00.000Z", None, True, False),
    ("2024-01-01", None, True, False),
    (None, "not-a-date", True, False),
], ids=["29-days", "31-days-override", "before-only", "31-days", "after-equals-before",
        "after-past-before", "offset", "fractional-seconds", "date-only", "bad-before"])
def test_validate_date_params(after, before, override, ok):
    if ok:
        _validate_date_params(after, before, override)
    else:
        with pytest.raises(HTTPException) as exc:
            _validate_date_params(after, before, override)
        assert exc.value.status_code == 400


# ---------------------------------------------------------------------------
# Header wiring through the granule endpoints
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def client():
    from app.auth import require_auth
    from app.main import app

    app.dependency_overrides[require_auth] = lambda: "test-token"
    yield TestClient(app, raise_server_exceptions=False)
    app.dependency_overrides.clear()


@pytest.fixture(autouse=True)
def mock_io(monkeypatch):
    monkeypatch.setattr("app.routers.reindex.job_store", MagicMock())
    monkeypatch.setattr("app.routers.reindex.start_scan", MagicMock())


@pytest.mark.parametrize("path", [
    "/reindexer/reindex/granules",
    "/reindexer/reindex/granules/provider/PROV_A",
    "/reindexer/reindex/granules/collection/C1234-PROV",
])
@pytest.mark.parametrize("header, status", [(None, 400), ("true", 202), ("TRUE", 202), ("false", 400)])
def test_override_header(client, path, header, status):
    headers = {"X-CMR-Override-Date-Limit": header} if header else {}
    assert client.post(path, params={"after": _days_ago(31)}, headers=headers).status_code == status
