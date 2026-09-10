"""
Unit tests for JobStore — all DynamoDB I/O is mocked.
"""
from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock

import pytest
from botocore.exceptions import ClientError

import app.db.dynamo as _dynamo_mod
from app.db.dynamo import JobStore


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def mock_table(monkeypatch):
    t = MagicMock()
    # get_item returns empty by default → get_job returns None
    t.get_item.return_value = {}
    t.scan.return_value = {"Items": []}
    monkeypatch.setattr(_dynamo_mod, "_dynamo_table", lambda: t)
    return t


@pytest.fixture
def store(mock_table):
    return JobStore()


# ---------------------------------------------------------------------------
# create_job
# ---------------------------------------------------------------------------

class TestCreateJob:

    def test_calls_put_item(self, store, mock_table):
        store.create_job("job-1", "granules")
        mock_table.put_item.assert_called_once()

    def test_status_is_running(self, store, mock_table):
        store.create_job("job-1", "granules")
        item = mock_table.put_item.call_args[1]["Item"]
        assert item["status"] == "running"

    def test_job_id_stored(self, store, mock_table):
        store.create_job("job-abc", "variables")
        item = mock_table.put_item.call_args[1]["Item"]
        assert item["job_id"] == "job-abc"

    def test_concept_type_stored(self, store, mock_table):
        store.create_job("job-1", "granules-by-provider")
        item = mock_table.put_item.call_args[1]["Item"]
        assert item["concept_type"] == "granules-by-provider"

    def test_optional_fields_included_when_provided(self, store, mock_table):
        store.create_job(
            "job-1", "granules-by-provider",
            provider_id="PROV",
            collection_id="C1-PROV",
            after="2024-01-01T00:00:00Z",
            before="2024-12-31T23:59:59Z",
        )
        item = mock_table.put_item.call_args[1]["Item"]
        assert item["provider_id"] == "PROV"
        assert item["collection_id"] == "C1-PROV"
        assert item["after"] == "2024-01-01T00:00:00Z"
        assert item["before"] == "2024-12-31T23:59:59Z"

    def test_optional_fields_absent_when_not_provided(self, store, mock_table):
        store.create_job("job-1", "granules")
        item = mock_table.put_item.call_args[1]["Item"]
        assert "provider_id" not in item
        assert "collection_id" not in item
        assert "after" not in item
        assert "before" not in item

    def test_ttl_is_approximately_30_days_from_now(self, store, mock_table):
        store.create_job("job-1", "granules")
        item = mock_table.put_item.call_args[1]["Item"]
        ttl = item["ttl"]
        expected = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp())
        assert abs(ttl - expected) < 10  # within 10 seconds

    def test_counters_initialised_to_zero(self, store, mock_table):
        store.create_job("job-1", "granules")
        item = mock_table.put_item.call_args[1]["Item"]
        assert item["work_items_enqueued"] == 0
        assert item["collections_split"] == 0
        assert item["total_dispatched"] == 0


# ---------------------------------------------------------------------------
# update_heartbeat
# ---------------------------------------------------------------------------

class TestUpdateHeartbeat:

    def test_updates_last_heartbeat(self, store, mock_table):
        store.update_heartbeat("job-1")
        values = mock_table.update_item.call_args[1]["ExpressionAttributeValues"]
        assert ":ts" in values


# ---------------------------------------------------------------------------
# update_progress
# ---------------------------------------------------------------------------

class TestUpdateProgress:

    def test_sets_providers_to_process(self, store, mock_table):
        store.update_progress("job-1", providers_to_process=["PROV1", "PROV2"])
        values = mock_table.update_item.call_args[1]["ExpressionAttributeValues"]
        assert ":ptp" in values
        assert values[":ptp"] == {"PROV1", "PROV2"}

    def test_adds_provider_to_enqueued_set(self, store, mock_table):
        store.update_progress("job-1", provider_enqueued="PROV1")
        call = mock_table.update_item.call_args[1]
        assert "providers_enqueued :pe" in call["UpdateExpression"]
        assert call["ExpressionAttributeValues"][":pe"] == {"PROV1"}

    def test_increments_work_items_delta(self, store, mock_table):
        store.update_progress("job-1", work_items_delta=50)
        call = mock_table.update_item.call_args[1]
        assert "work_items_enqueued :wi" in call["UpdateExpression"]
        assert call["ExpressionAttributeValues"][":wi"] == 50

    def test_always_updates_heartbeat(self, store, mock_table):
        store.update_progress("job-1")
        values = mock_table.update_item.call_args[1]["ExpressionAttributeValues"]
        assert ":ts" in values


# ---------------------------------------------------------------------------
# update_dispatched
# ---------------------------------------------------------------------------

class TestUpdateDispatched:

    def test_adds_count_to_total_dispatched(self, store, mock_table):
        store.update_dispatched("job-1", 42)
        call = mock_table.update_item.call_args[1]
        assert "ADD total_dispatched :n" in call["UpdateExpression"]
        assert call["ExpressionAttributeValues"][":n"] == 42


# ---------------------------------------------------------------------------
# increment_collections_split
# ---------------------------------------------------------------------------

class TestIncrementCollectionsSplit:

    def test_add_expression_increments_collections_split_by_one(self, store, mock_table):
        store.increment_collections_split("job-1")
        call = mock_table.update_item.call_args[1]
        assert "collections_split :one" in call["UpdateExpression"]
        assert call["ExpressionAttributeValues"][":one"] == 1

    def test_updates_heartbeat(self, store, mock_table):
        store.increment_collections_split("job-1")
        values = mock_table.update_item.call_args[1]["ExpressionAttributeValues"]
        assert ":ts" in values

    def test_correct_job_id_used_as_key(self, store, mock_table):
        store.increment_collections_split("job-xyz")
        key = mock_table.update_item.call_args[1]["Key"]
        assert key == {"job_id": "job-xyz"}


# ---------------------------------------------------------------------------
# mark_job
# ---------------------------------------------------------------------------

class TestMarkJob:

    def test_sets_status(self, store, mock_table):
        store.mark_job("job-1", "completed")
        values = mock_table.update_item.call_args[1]["ExpressionAttributeValues"]
        assert values[":s"] == "completed"

    def test_terminal_status_sets_completed_at(self, store, mock_table):
        for status in ("completed", "failed", "interrupted", "cancelled"):
            mock_table.reset_mock()
            store.mark_job("job-1", status)
            expr = mock_table.update_item.call_args[1]["UpdateExpression"]
            assert "completed_at" in expr, f"expected completed_at for status={status}"

    def test_non_terminal_status_no_completed_at(self, store, mock_table):
        store.mark_job("job-1", "running")
        expr = mock_table.update_item.call_args[1]["UpdateExpression"]
        assert "completed_at" not in expr

    def test_dispatching_is_non_terminal(self, store, mock_table):
        store.mark_job("job-1", "dispatching")
        expr = mock_table.update_item.call_args[1]["UpdateExpression"]
        assert "completed_at" not in expr

    def test_always_includes_condition_expression_to_protect_terminal_statuses(self, store, mock_table):
        """mark_job must never overwrite a terminal status — condition expression required on all paths."""
        for status in ("dispatching", "running", "completed", "failed", "cancelled", "interrupted"):
            mock_table.reset_mock()
            store.mark_job("job-1", status)
            kwargs = mock_table.update_item.call_args[1]
            assert "ConditionExpression" in kwargs, (
                f"mark_job({status!r}) must carry a ConditionExpression"
            )
            values = kwargs["ExpressionAttributeValues"]
            assert ":cancelled" in values, "condition must guard against overwriting 'cancelled'"

    def test_returns_false_when_already_in_terminal_status(self, store, mock_table):
        """mark_job returns False (no-op) when DynamoDB rejects the condition."""
        from botocore.exceptions import ClientError
        mock_table.update_item.side_effect = ClientError(
            {"Error": {"Code": "ConditionalCheckFailedException", "Message": "condition failed"}},
            "UpdateItem",
        )
        result = store.mark_job("job-1", "dispatching")
        assert result is False

    def test_returns_true_on_successful_update(self, store, mock_table):
        mock_table.update_item.return_value = {}
        result = store.mark_job("job-1", "dispatching")
        assert result is True


# ---------------------------------------------------------------------------
# get_job
# ---------------------------------------------------------------------------

class TestGetJob:

    def test_returns_none_when_item_absent(self, store, mock_table):
        mock_table.get_item.return_value = {}
        assert store.get_job("missing") is None

    def test_returns_item_when_present(self, store, mock_table):
        mock_table.get_item.return_value = {"Item": {"job_id": "j1", "status": "running"}}
        job = store.get_job("j1")
        assert job["job_id"] == "j1"
        assert job["status"] == "running"


# ---------------------------------------------------------------------------
# find_stalled_jobs
# ---------------------------------------------------------------------------

class TestFindStalledJobs:

    def test_scans_for_running_status(self, store, mock_table):
        store.find_stalled_jobs()
        values = mock_table.scan.call_args[1]["ExpressionAttributeValues"]
        assert values[":running"] == "running"

    def test_scans_for_dispatching_status(self, store, mock_table):
        store.find_stalled_jobs()
        values = mock_table.scan.call_args[1]["ExpressionAttributeValues"]
        assert values[":dispatching"] == "dispatching"

    def test_returns_empty_list_when_no_stalled(self, store, mock_table):
        mock_table.scan.return_value = {"Items": []}
        assert store.find_stalled_jobs() == []

    def test_returns_items_from_scan(self, store, mock_table):
        mock_table.scan.return_value = {"Items": [{"job_id": "stalled-1", "status": "running"}]}
        jobs = store.find_stalled_jobs()
        assert len(jobs) == 1
        assert jobs[0]["job_id"] == "stalled-1"


# ---------------------------------------------------------------------------
# claim_stalled_job
# ---------------------------------------------------------------------------

class TestListJobs:

    def test_returns_empty_list_when_no_jobs(self, store, mock_table):
        mock_table.scan.return_value = {"Items": []}
        result = store.list_jobs()
        assert result == []

    def test_returns_all_jobs_up_to_limit(self, store, mock_table):
        mock_table.scan.return_value = {"Items": [{"job_id": f"job-{i}", "status": "completed"} for i in range(5)]}
        result = store.list_jobs(limit=3)
        assert len(result) == 3

    def test_no_filter_scans_without_filter_expression(self, store, mock_table):
        mock_table.scan.return_value = {"Items": []}
        store.list_jobs()
        call = mock_table.scan.call_args[1]
        assert "FilterExpression" not in call

    def test_status_filter_adds_filter_expression(self, store, mock_table):
        mock_table.scan.return_value = {"Items": []}
        store.list_jobs(status_filter="running")
        call = mock_table.scan.call_args[1]
        assert "FilterExpression" in call
        assert call["ExpressionAttributeValues"][":s"] == "running"

    def test_returns_deserialized_items(self, store, mock_table):
        import decimal
        mock_table.scan.return_value = {"Items": [{"job_id": "j1", "total_dispatched": decimal.Decimal(42)}]}
        result = store.list_jobs()
        assert result[0]["total_dispatched"] == 42


class TestTryCompleteJob:

    def test_returns_true_when_update_succeeds(self, store, mock_table):
        assert store.try_complete_job("job-1") is True

    def test_returns_false_on_condition_check_failure(self, store, mock_table):
        mock_table.update_item.side_effect = ClientError(
            {"Error": {"Code": "ConditionalCheckFailedException", "Message": "..."}},
            "UpdateItem",
        )
        assert store.try_complete_job("job-1") is False

    def test_reraises_unexpected_errors(self, store, mock_table):
        mock_table.update_item.side_effect = ClientError(
            {"Error": {"Code": "InternalServerError", "Message": "boom"}},
            "UpdateItem",
        )
        with pytest.raises(ClientError):
            store.try_complete_job("job-1")

    def test_sets_status_to_completed(self, store, mock_table):
        store.try_complete_job("job-1")
        call = mock_table.update_item.call_args[1]
        assert call["ExpressionAttributeValues"][":completed"] == "completed"

    def test_sets_completed_at_in_expression(self, store, mock_table):
        store.try_complete_job("job-1")
        expr = mock_table.update_item.call_args[1]["UpdateExpression"]
        assert "completed_at" in expr

    def test_condition_requires_dispatching_status(self, store, mock_table):
        store.try_complete_job("job-1")
        call = mock_table.update_item.call_args[1]
        assert call["ExpressionAttributeValues"][":dispatching"] == "dispatching"

    def test_condition_checks_collections_split_equals_work_items_enqueued(self, store, mock_table):
        store.try_complete_job("job-1")
        cond = mock_table.update_item.call_args[1]["ConditionExpression"]
        assert "collections_split = work_items_enqueued" in cond

    def test_uses_correct_job_id_as_key(self, store, mock_table):
        store.try_complete_job("job-xyz")
        key = mock_table.update_item.call_args[1]["Key"]
        assert key == {"job_id": "job-xyz"}


class TestFindInterruptedJobs:

    def test_filter_targets_interrupted_status(self, store, mock_table):
        store.find_interrupted_jobs()
        call = mock_table.scan.call_args[1]
        assert call["ExpressionAttributeValues"][":interrupted"] == "interrupted"

    def test_filter_expression_references_status_attribute(self, store, mock_table):
        store.find_interrupted_jobs()
        call = mock_table.scan.call_args[1]
        assert "#st" in call["ExpressionAttributeNames"]
        assert call["ExpressionAttributeNames"]["#st"] == "status"

    def test_returns_empty_list_when_none_found(self, store, mock_table):
        assert store.find_interrupted_jobs() == []

    def test_returns_deserialized_items(self, store, mock_table):
        import decimal
        mock_table.scan.return_value = {"Items": [{"job_id": "j1", "total_dispatched": decimal.Decimal(5)}]}
        results = store.find_interrupted_jobs()
        assert results[0]["total_dispatched"] == 5
        assert isinstance(results[0]["total_dispatched"], int)


class TestTryMarkInterrupted:

    def test_returns_true_on_success(self, store, mock_table):
        assert store.try_mark_interrupted("job-1") is True

    def test_returns_false_on_conditional_check_failure(self, store, mock_table):
        mock_table.update_item.side_effect = ClientError(
            {"Error": {"Code": "ConditionalCheckFailedException", "Message": "..."}},
            "UpdateItem",
        )
        assert store.try_mark_interrupted("job-1") is False

    def test_reraises_unexpected_errors(self, store, mock_table):
        mock_table.update_item.side_effect = ClientError(
            {"Error": {"Code": "InternalServerError", "Message": "boom"}},
            "UpdateItem",
        )
        with pytest.raises(ClientError):
            store.try_mark_interrupted("job-1")

    def test_sets_status_to_interrupted(self, store, mock_table):
        store.try_mark_interrupted("job-1")
        values = mock_table.update_item.call_args[1]["ExpressionAttributeValues"]
        assert values[":interrupted"] == "interrupted"

    def test_sets_completed_at_in_expression(self, store, mock_table):
        store.try_mark_interrupted("job-1")
        expr = mock_table.update_item.call_args[1]["UpdateExpression"]
        assert "completed_at" in expr

    def test_condition_excludes_completed(self, store, mock_table):
        store.try_mark_interrupted("job-1")
        cond = mock_table.update_item.call_args[1]["ConditionExpression"]
        assert "<> :completed" in cond

    def test_condition_excludes_failed(self, store, mock_table):
        store.try_mark_interrupted("job-1")
        cond = mock_table.update_item.call_args[1]["ConditionExpression"]
        assert "<> :failed" in cond

    def test_condition_excludes_already_interrupted(self, store, mock_table):
        # idempotent — already interrupted should not overwrite itself
        store.try_mark_interrupted("job-1")
        cond = mock_table.update_item.call_args[1]["ConditionExpression"]
        assert "<> :interrupted" in cond

    def test_condition_excludes_cancelled(self, store, mock_table):
        # a cancelled job must never be overwritten with interrupted
        store.try_mark_interrupted("job-1")
        cond = mock_table.update_item.call_args[1]["ConditionExpression"]
        assert "<> :cancelled" in cond

    def test_condition_allows_running(self, store, mock_table):
        # running jobs are the primary target of this call
        store.try_mark_interrupted("job-1")
        cond = mock_table.update_item.call_args[1]["ConditionExpression"]
        assert "<> :running" not in cond

    def test_condition_allows_dispatching(self, store, mock_table):
        # dispatching is the normal granule-job state during dispatch
        store.try_mark_interrupted("job-1")
        cond = mock_table.update_item.call_args[1]["ConditionExpression"]
        assert "<> :dispatching" not in cond

    def test_uses_correct_job_id_as_key(self, store, mock_table):
        store.try_mark_interrupted("job-xyz")
        key = mock_table.update_item.call_args[1]["Key"]
        assert key == {"job_id": "job-xyz"}


class TestTryCancelJob:

    def test_returns_true_when_update_succeeds(self, store, mock_table):
        assert store.try_cancel_job("job-1") is True

    def test_returns_false_on_condition_check_failure(self, store, mock_table):
        mock_table.update_item.side_effect = ClientError(
            {"Error": {"Code": "ConditionalCheckFailedException", "Message": "..."}},
            "UpdateItem",
        )
        assert store.try_cancel_job("job-1") is False

    def test_reraises_unexpected_errors(self, store, mock_table):
        mock_table.update_item.side_effect = ClientError(
            {"Error": {"Code": "InternalServerError", "Message": "boom"}},
            "UpdateItem",
        )
        with pytest.raises(ClientError):
            store.try_cancel_job("job-1")

    def test_sets_status_to_cancelled(self, store, mock_table):
        store.try_cancel_job("job-1")
        values = mock_table.update_item.call_args[1]["ExpressionAttributeValues"]
        assert values[":cancelled"] == "cancelled"

    def test_sets_completed_at_in_expression(self, store, mock_table):
        store.try_cancel_job("job-1")
        expr = mock_table.update_item.call_args[1]["UpdateExpression"]
        assert "completed_at" in expr

    def test_condition_excludes_completed(self, store, mock_table):
        store.try_cancel_job("job-1")
        cond = mock_table.update_item.call_args[1]["ConditionExpression"]
        assert "<> :completed" in cond

    def test_condition_excludes_failed(self, store, mock_table):
        store.try_cancel_job("job-1")
        cond = mock_table.update_item.call_args[1]["ConditionExpression"]
        assert "<> :failed" in cond

    def test_condition_allows_interrupted(self, store, mock_table):
        # interrupted jobs can be cancelled — they must NOT be excluded by the condition
        store.try_cancel_job("job-1")
        cond = mock_table.update_item.call_args[1]["ConditionExpression"]
        assert "<> :interrupted" not in cond

    def test_condition_excludes_already_cancelled(self, store, mock_table):
        store.try_cancel_job("job-1")
        cond = mock_table.update_item.call_args[1]["ConditionExpression"]
        assert "<> :cancelled" in cond

    def test_uses_correct_job_id_as_key(self, store, mock_table):
        store.try_cancel_job("job-xyz")
        key = mock_table.update_item.call_args[1]["Key"]
        assert key == {"job_id": "job-xyz"}


class TestClaimStalledJob:

    def test_returns_true_on_success(self, store, mock_table):
        assert store.claim_stalled_job("job-1", "2026-08-24T00:00:00Z") is True

    def test_returns_false_on_conditional_check_failure(self, store, mock_table):
        mock_table.update_item.side_effect = ClientError(
            {"Error": {"Code": "ConditionalCheckFailedException", "Message": "..."}},
            "UpdateItem",
        )
        assert store.claim_stalled_job("job-1", "2026-08-24T00:00:00Z") is False

    def test_reraises_unexpected_errors(self, store, mock_table):
        mock_table.update_item.side_effect = ClientError(
            {"Error": {"Code": "InternalServerError", "Message": "boom"}},
            "UpdateItem",
        )
        with pytest.raises(ClientError):
            store.claim_stalled_job("job-1", "2026-08-24T00:00:00Z")

    def test_condition_expression_uses_last_heartbeat_for_atomic_claim(self, store, mock_table):
        """The optimistic lock: only the caller that saw this exact heartbeat can claim the job."""
        store.claim_stalled_job("job-1", "2026-08-24T00:00:00Z")
        call = mock_table.update_item.call_args[1]
        assert "ConditionExpression" in call
        assert "last_heartbeat" in call["ConditionExpression"]
        assert call["ExpressionAttributeValues"][":expected"] == "2026-08-24T00:00:00Z"


# ---------------------------------------------------------------------------
# CheckpointStore
# ---------------------------------------------------------------------------

@pytest.fixture
def mock_checkpoint_table(monkeypatch):
    t = MagicMock()
    t.get_item.return_value = {}
    monkeypatch.setattr(_dynamo_mod, "_checkpoint_table", lambda: t)
    return t


@pytest.fixture
def checkpoint_store(mock_checkpoint_table):
    from app.db.dynamo import CheckpointStore
    return CheckpointStore()


class TestCheckpointStore:

    def test_write_uses_put_item(self, checkpoint_store, mock_checkpoint_table):
        checkpoint_store.write_collection_checkpoint("job-1", "C1-PROV", "G500-PROV", 500, 5)
        mock_checkpoint_table.put_item.assert_called_once()

    def test_write_stores_all_fields(self, checkpoint_store, mock_checkpoint_table):
        checkpoint_store.write_collection_checkpoint("job-1", "C1-PROV", "G500-PROV", 500, 5)
        item = mock_checkpoint_table.put_item.call_args[1]["Item"]
        assert item["job_id"] == "job-1"
        assert item["collection_id"] == "C1-PROV"
        assert item["last_concept_id"] == "G500-PROV"
        assert item["granules_dispatched"] == 500
        assert item["chunks_dispatched"] == 5

    def test_write_ttl_approximately_30_days(self, checkpoint_store, mock_checkpoint_table):
        checkpoint_store.write_collection_checkpoint("job-1", "C1-PROV", "G1-PROV", 1, 1)
        item = mock_checkpoint_table.put_item.call_args[1]["Item"]
        expected = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp())
        assert abs(item["ttl"] - expected) < 10

    def test_get_returns_none_when_no_checkpoint(self, checkpoint_store, mock_checkpoint_table):
        mock_checkpoint_table.get_item.return_value = {}
        assert checkpoint_store.get_collection_checkpoint("job-1", "C1-PROV") is None

    def test_get_returns_checkpoint_when_present(self, checkpoint_store, mock_checkpoint_table):
        mock_checkpoint_table.get_item.return_value = {
            "Item": {
                "job_id": "job-1",
                "collection_id": "C1-PROV",
                "last_concept_id": "G100-PROV",
                "granules_dispatched": 100,
                "chunks_dispatched": 2,
            }
        }
        ckpt = checkpoint_store.get_collection_checkpoint("job-1", "C1-PROV")
        assert ckpt is not None
        assert ckpt["last_concept_id"] == "G100-PROV"
        assert ckpt["granules_dispatched"] == 100

    def test_get_uses_composite_key(self, checkpoint_store, mock_checkpoint_table):
        checkpoint_store.get_collection_checkpoint("job-abc", "C99-XYZ")
        key = mock_checkpoint_table.get_item.call_args[1]["Key"]
        assert key == {"job_id": "job-abc", "collection_id": "C99-XYZ"}

    def test_delete_uses_delete_item(self, checkpoint_store, mock_checkpoint_table):
        checkpoint_store.delete_collection_checkpoint("job-1", "C1-PROV")
        mock_checkpoint_table.delete_item.assert_called_once()

    def test_delete_uses_composite_key(self, checkpoint_store, mock_checkpoint_table):
        checkpoint_store.delete_collection_checkpoint("job-xyz", "C55-PROV")
        key = mock_checkpoint_table.delete_item.call_args[1]["Key"]
        assert key == {"job_id": "job-xyz", "collection_id": "C55-PROV"}
