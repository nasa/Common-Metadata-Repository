"""
Unit tests for indexer event bodies and batched sends. _sqs() is replaced with a mock
client, so lru_cache never holds a real connection.
"""
import json
from unittest.mock import MagicMock

import pytest

import app.sqs.client as _sqs_mod
from app.config import config
from app.sqs.client import publish_concept_update, publish_indexer_events_batch


@pytest.fixture
def sqs(monkeypatch):
    mock = MagicMock()
    mock.send_message_batch.return_value = {"Successful": [], "Failed": []}
    monkeypatch.setattr(_sqs_mod, "_sqs", lambda: mock)
    return mock


def test_publish_concept_update_body(sqs):
    publish_concept_update("G9876-TESTPROV", 42, "req-1")
    kwargs = sqs.send_message.call_args.kwargs
    assert kwargs["QueueUrl"] == config.indexer_queue_url
    assert json.loads(kwargs["MessageBody"]) == {"action": "concept-update", "concept-id": "G9876-TESTPROV", "revision-id": 42}


def test_batch_bodies_and_ids(sqs):
    publish_indexer_events_batch([("G1-PROV", 3, 1), ("G2-PROV", 1, 0), ("G3-PROV", 2)], "req-1")
    kwargs = sqs.send_message_batch.call_args.kwargs
    assert kwargs["QueueUrl"] == config.indexer_queue_url
    assert [e["Id"] for e in kwargs["Entries"]] == ["0", "1", "2"]
    assert [json.loads(e["MessageBody"]) for e in kwargs["Entries"]] == [
        {"action": "concept-delete", "concept-id": "G1-PROV", "revision-id": 3},
        {"action": "concept-update", "concept-id": "G2-PROV", "revision-id": 1},
        {"action": "concept-update", "concept-id": "G3-PROV", "revision-id": 2},
    ]


@pytest.mark.parametrize("n, sizes", [(10, [10]), (21, [1, 10, 10])])
def test_records_split_into_batches_of_ten(sqs, n, sizes):
    publish_indexer_events_batch([(f"G{i}-P", i) for i in range(n)], "req-1")
    # Batches are sent concurrently, so compare sorted sizes.
    assert sorted(len(c.kwargs["Entries"]) for c in sqs.send_message_batch.call_args_list) == sizes


def test_partial_failure_raises(sqs):
    sqs.send_message_batch.return_value = {
        "Successful": [], "Failed": [{"Id": "0", "Code": "InternalError", "Message": "SQS blew up"}],
    }
    with pytest.raises(RuntimeError, match="partial failure"):
        publish_indexer_events_batch([("G1-PROV", 1)], "req-1")


def test_get_queue_counts_splits_available_and_in_flight(sqs):
    sqs.get_queue_attributes.return_value = {"Attributes": {
        "ApproximateNumberOfMessages": "7", "ApproximateNumberOfMessagesNotVisible": "2",
    }}
    assert _sqs_mod.get_queue_counts("http://sqs/q") == {"available": 7, "in_flight": 2}
