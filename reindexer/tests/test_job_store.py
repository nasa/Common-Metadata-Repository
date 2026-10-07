"""
Unit tests for JobStore — all DynamoDB I/O is mocked.
"""
import decimal
from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock

import pytest
from botocore.exceptions import ClientError

import app.db.dynamo as _dynamo_mod
from app.db.dynamo import JobStore


@pytest.fixture
def mock_table(monkeypatch):
    t = MagicMock()
    t.get_item.return_value = {}
    t.scan.return_value = {"Items": []}
    monkeypatch.setattr(_dynamo_mod, "_dynamo_table", lambda: t)
    return t


@pytest.fixture
def store(mock_table):
    return JobStore()


def _client_error(code):
    return ClientError({"Error": {"Code": code, "Message": "..."}}, "UpdateItem")


def _expires_in_30_days(ttl):
    return abs(ttl - (datetime.now(timezone.utc) + timedelta(days=30)).timestamp()) < 10


# ---------------------------------------------------------------------------
# create_job
# ---------------------------------------------------------------------------

class TestCreateJob:

    def test_minimal_item(self, store, mock_table):
        store.create_job("job-1", "granules")
        item = mock_table.put_item.call_args[1]["Item"]
        assert item["started_at"] == item["last_heartbeat"]
        assert {k: v for k, v in item.items() if k not in ("started_at", "last_heartbeat")} == {
            "job_id": "job-1", "status": "running", "concept_type": "granules", "total_dispatched": 0,
        }

    def test_optional_fields(self, store, mock_table):
        store.create_job(
            "job-1", "granules-by-provider", providers=["PROV"], collection_id="C1-PROV",
            concept_id="V1-PROV", after="2024-01-01T00:00:00Z", before="2024-12-31T23:59:59Z",
            source_url="/reindexer/reindex/granules/provider/PROV",
        )
        item = mock_table.put_item.call_args[1]["Item"]
        assert item["providers_requested"] == ["PROV"]
        assert (item["collection_id"], item["concept_id"]) == ("C1-PROV", "V1-PROV")
        assert (item["after"], item["before"]) == ("2024-01-01T00:00:00Z", "2024-12-31T23:59:59Z")
        assert item["source_url"] == "/reindexer/reindex/granules/provider/PROV"


# ---------------------------------------------------------------------------
# Progress writes
# ---------------------------------------------------------------------------

def test_update_dispatched_adds_to_the_total(store, mock_table):
    store.update_dispatched("job-1", 42)
    call = mock_table.update_item.call_args[1]
    assert "ADD total_dispatched :n" in call["UpdateExpression"]
    assert call["ExpressionAttributeValues"][":n"] == 42


class TestScanCursor:

    def test_cursor_saved_with_its_provider(self, store, mock_table):
        store.update_scan_cursor("job-1", 1500, provider_id="PROV")
        kwargs = mock_table.update_item.call_args.kwargs
        assert kwargs["UpdateExpression"] == "SET scan_cursor = :c, last_heartbeat = :ts, scan_provider = :p"
        assert (kwargs["ExpressionAttributeValues"][":c"], kwargs["ExpressionAttributeValues"][":p"]) == (1500, "PROV")

    def test_finish_provider_clears_its_cursor_in_the_same_write(self, store, mock_table):
        store.finish_provider("job-1", "PROV")
        kwargs = mock_table.update_item.call_args.kwargs
        assert "ADD providers_done :p" in kwargs["UpdateExpression"]
        assert "REMOVE scan_cursor, scan_provider" in kwargs["UpdateExpression"]
        assert kwargs["ExpressionAttributeValues"][":p"] == {"PROV"}


# ---------------------------------------------------------------------------
# Status writes
# ---------------------------------------------------------------------------

_GUARD = "#st <> :completed AND #st <> :failed AND #st <> :cancelled"


@pytest.mark.parametrize("status, finished", [("running", False), ("failed", True)])
def test_mark_job(store, mock_table, status, finished):
    """Never overwrites a finished status; a finished job gets completed_at and its TTL."""
    store.mark_job("job-1", status)
    call = mock_table.update_item.call_args[1]
    assert call["ConditionExpression"] == _GUARD
    assert call["ExpressionAttributeValues"][":s"] == status
    assert ("completed_at" in call["UpdateExpression"]) is finished
    ttl = call["ExpressionAttributeValues"].get(":ttl")
    assert _expires_in_30_days(ttl) if finished else ttl is None


def test_try_cancel_job(store, mock_table):
    store.try_cancel_job("job-1")
    call = mock_table.update_item.call_args[1]
    assert call["ConditionExpression"] == _GUARD
    assert "completed_at" in call["UpdateExpression"]
    assert call["ExpressionAttributeValues"][":cancelled"] == "cancelled"
    assert _expires_in_30_days(call["ExpressionAttributeValues"][":ttl"])


def test_claim_lapsed_job_only_matches_the_heartbeat_seen(store, mock_table):
    store.claim_lapsed_job("job-1", "2026-08-24T00:00:00Z")
    call = mock_table.update_item.call_args[1]
    assert call["ConditionExpression"] == "last_heartbeat = :expected"
    assert call["ExpressionAttributeValues"][":expected"] == "2026-08-24T00:00:00Z"


@pytest.mark.parametrize("write", [
    lambda s: s.mark_job("job-1", "running"),
    lambda s: s.try_cancel_job("job-1"),
    lambda s: s.claim_lapsed_job("job-1", "2026-08-24T00:00:00Z"),
], ids=["mark_job", "try_cancel_job", "claim_lapsed_job"])
@pytest.mark.parametrize("code, raises", [("ConditionalCheckFailedException", False), ("InternalServerError", True)])
def test_conditional_writes(store, mock_table, write, code, raises):
    """A failed condition returns False; any other error propagates."""
    assert write(store) is True
    mock_table.update_item.side_effect = _client_error(code)
    if raises:
        with pytest.raises(ClientError):
            write(store)
    else:
        assert write(store) is False


# ---------------------------------------------------------------------------
# Reads
# ---------------------------------------------------------------------------

def test_get_job_returns_none_when_absent(store, mock_table):
    assert store.get_job("missing") is None


def test_get_job_deserializes(store, mock_table):
    mock_table.get_item.return_value = {"Item": {
        "job_id": "j1",
        "scan_cursor": decimal.Decimal(1500),
        "providers_done": {"PROV_A"},
        "nested": {"n": decimal.Decimal(15)},
    }}
    job = store.get_job("j1")
    assert job == {"job_id": "j1", "scan_cursor": 1500, "providers_done": ["PROV_A"], "nested": {"n": 15}}
    assert isinstance(job["scan_cursor"], int)


def test_list_jobs_filters_by_status_and_pages_until_limit(store, mock_table):
    mock_table.scan.side_effect = [
        {"Items": [{"job_id": "a"}], "LastEvaluatedKey": {"job_id": "a"}},
        {"Items": [{"job_id": "b"}, {"job_id": "c"}], "LastEvaluatedKey": {"job_id": "c"}},
    ]
    assert [j["job_id"] for j in store.list_jobs(status_filter="running", limit=2)] == ["a", "b"]
    calls = mock_table.scan.call_args_list
    assert calls[0].kwargs["ExpressionAttributeValues"][":s"] == "running"
    assert calls[1].kwargs["ExclusiveStartKey"] == {"job_id": "a"}


def test_find_cancelled_jobs_reads_every_page(store, mock_table):
    mock_table.scan.side_effect = [
        {"Items": [{"job_id": "a"}], "LastEvaluatedKey": {"job_id": "a"}},
        {"Items": [{"job_id": "b"}]},
    ]
    assert [j["job_id"] for j in store.find_cancelled_jobs()] == ["a", "b"]


def test_find_lapsed_jobs_targets_running_jobs_past_the_lease(store, mock_table):
    store.find_lapsed_jobs(5)
    call = mock_table.scan.call_args[1]
    assert call["FilterExpression"] == "#st = :running AND last_heartbeat < :cutoff"
    values = call["ExpressionAttributeValues"]
    assert values[":running"] == "running"
    cutoff = datetime.strptime(values[":cutoff"], "%Y-%m-%dT%H:%M:%SZ")
    expected = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(minutes=5)
    assert abs((cutoff - expected).total_seconds()) < 5
