"""Regression coverage for shaped but invalid reindex date parameters."""

from datetime import datetime, timezone
from unittest.mock import MagicMock

from fastapi import HTTPException
from fastapi.testclient import TestClient
import pytest

from app.auth import require_auth
from app.main import app
from app.routers.reindex import _parse_utc_z


@pytest.mark.parametrize("param_name", ["after", "before"])
@pytest.mark.parametrize(
    "value",
    [
        "2026-02-29T00:00:00Z",
        "2026-02-30T00:00:00Z",
        "2026-04-31T00:00:00Z",
        "2026-13-01T00:00:00Z",
        "2026-00-01T00:00:00Z",
        "2026-01-00T00:00:00Z",
        "2026-01-01T24:00:00Z",
        "2026-01-01T00:60:00Z",
    ],
)
def test_invalid_calendar_values_raise_http_400(value, param_name):
    """Calendar validation must have the same status as format validation."""
    with pytest.raises(HTTPException) as error:
        _parse_utc_z(value, param_name)
    assert error.value.status_code == 400
    assert param_name in error.value.detail


@pytest.mark.parametrize(
    "value, expected",
    [
        ("2024-02-29T00:00:00Z", datetime(2024, 2, 29, tzinfo=timezone.utc)),
        (
            "2000-02-29T12:34:56Z",
            datetime(2000, 2, 29, 12, 34, 56, tzinfo=timezone.utc),
        ),
        (
            "2026-12-31T23:59:59Z",
            datetime(2026, 12, 31, 23, 59, 59, tzinfo=timezone.utc),
        ),
    ],
)
def test_valid_calendar_values_are_unchanged(value, expected):
    """Keep leap days and ordinary boundary timestamps valid."""
    assert _parse_utc_z(value, "before") == expected


@pytest.mark.parametrize(
    "route",
    [
        "/reindexer/reindex/granules",
        "/reindexer/reindex/granules/provider/PROV",
        "/reindexer/reindex/granules/collection/C1234-PROV",
    ],
)
@pytest.mark.parametrize("param_name", ["after", "before"])
def test_invalid_calendar_date_is_rejected_before_side_effects(
    monkeypatch, route, param_name
):
    """Exercise the actual ASGI routes without external clients or a lifespan."""
    store = MagicMock()
    enqueue = MagicMock()
    monkeypatch.setattr("app.routers.reindex.job_store", store)
    monkeypatch.setattr("app.routers.reindex.enqueue_collection_item", enqueue)
    monkeypatch.setitem(app.dependency_overrides, require_auth, lambda: "test-token")
    client = TestClient(app, raise_server_exceptions=False)
    try:
        response = client.post(route, params={param_name: "2026-02-30T00:00:00Z"})
    finally:
        client.close()
    assert response.status_code == 400
    assert param_name in response.json()["detail"]
    assert store.mock_calls == []
    enqueue.assert_not_called()
