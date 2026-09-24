"""Cancelled-message cleanup failures must not stop the reindex worker."""

import threading
from unittest.mock import Mock, call

from botocore.exceptions import ClientError, EndpointConnectionError
import pytest

from app.sqs.schemas import CollectionWorkItem
from app.throttler import worker as worker_module
from app.throttler.worker import ThrottlerWorker


def work_message(job_id, receipt):
    return {
        "ReceiptHandle": receipt,
        "Body": CollectionWorkItem(
            request_id=job_id, collection_id="C1-PROV"
        ).to_json(),
    }


@pytest.fixture(params=["throttled", "connection", "runtime"])
def deletion_error(request):
    if request.param == "throttled":
        return ClientError(
            {"Error": {"Code": "RequestThrottled", "Message": "Try again"}},
            "DeleteMessage",
        )
    if request.param == "connection":
        return EndpointConnectionError(endpoint_url="https://sqs.example.test")
    return RuntimeError("temporary deletion failure")


@pytest.fixture
def worker(monkeypatch):
    instance = ThrottlerWorker()
    cache = Mock()
    cache.is_cancelled.side_effect = lambda job_id: job_id == "cancelled"
    instance.set_cancel_cache(cache)
    monkeypatch.setattr(instance, "_handle_collection", Mock())
    return instance


def test_cancelled_deletion_error_is_logged_without_processing(
    worker, deletion_error, monkeypatch, caplog
):
    delete = Mock(side_effect=deletion_error)
    monkeypatch.setattr(worker_module, "delete_message", delete)
    worker._process(work_message("cancelled", "first-receipt"), "fixture-queue")
    delete.assert_called_once_with("fixture-queue", "first-receipt")
    worker._handle_collection.assert_not_called()
    assert worker.current_job_id is None
    warnings = [
        record.msg for record in caplog.records if record.levelname == "WARNING"
    ]
    assert warnings == [
        {"event": "delete_message_failed", "error": str(deletion_error)}
    ]


def test_cancelled_message_can_be_deleted_after_redelivery(worker, monkeypatch):
    delete = Mock(side_effect=[RuntimeError("temporary failure"), None])
    monkeypatch.setattr(worker_module, "delete_message", delete)
    worker._process(work_message("cancelled", "old-receipt"), "fixture-queue")
    worker._process(work_message("cancelled", "new-receipt"), "fixture-queue")
    assert delete.call_args_list == [
        call("fixture-queue", "old-receipt"),
        call("fixture-queue", "new-receipt"),
    ]
    worker._handle_collection.assert_not_called()


def test_successful_cancelled_deletion_remains_unchanged(worker, monkeypatch, caplog):
    delete = Mock()
    monkeypatch.setattr(worker_module, "delete_message", delete)
    worker._process(work_message("cancelled", "receipt"), "fixture-queue")
    delete.assert_called_once_with("fixture-queue", "receipt")
    worker._handle_collection.assert_not_called()
    assert not [record for record in caplog.records if record.levelname == "WARNING"]


@pytest.mark.parametrize("threaded", [False, True])
def test_worker_continues_to_active_jobs(worker, deletion_error, threaded, monkeypatch):
    """Exercise the real receive/process loop and, optionally, an actual thread."""
    cancelled = work_message("cancelled", "cancelled-receipt")
    active = work_message("active", "active-receipt")
    messages = iter([[cancelled, active]])

    def receive(*_args, **_kwargs):
        batch = next(messages, None)
        if batch is None:
            worker._stop_event.set()
            return []
        return batch

    delete = Mock(side_effect=[deletion_error, None])
    monkeypatch.setattr(worker_module, "delete_message", delete)
    monkeypatch.setattr(worker_module, "receive_messages", receive)
    monkeypatch.setattr(
        worker_module, "check_all_es_health", lambda: {"overall": "green"}
    )
    errors = []

    def run():
        try:
            worker._run()
        except Exception as error:
            errors.append(error)

    if threaded:
        thread = threading.Thread(target=run)
        try:
            thread.start()
            thread.join(timeout=5)
        finally:
            worker._stop_event.set()
            thread.join(timeout=5)
        assert not thread.is_alive()
    else:
        run()
    assert errors == []
    worker._handle_collection.assert_called_once_with(
        CollectionWorkItem(request_id="active", collection_id="C1-PROV")
    )
    assert [c.args[1] for c in delete.call_args_list] == [
        "cancelled-receipt",
        "active-receipt",
    ]
    assert worker.current_job_id is None
