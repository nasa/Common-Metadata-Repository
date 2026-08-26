"""
Unit tests for SQS client message body and queue routing.

Verifies the JSON shape sent by enqueue_collection_item, enqueue_page_item,
and publish_concept_update — in particular that publish_concept_update uses
hyphenated keys ("concept-id", "revision-id") matching the CMR indexer contract.

_sqs() is replaced with a lambda returning a mock boto3 client so that
lru_cache never caches a real connection during tests.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_sqs_client.py -v
"""
import json
from unittest.mock import MagicMock

import pytest

import app.sqs.client as _sqs_mod
from app.config import config
from app.sqs.client import enqueue_collection_item, enqueue_page_item, publish_concept_update


# ---------------------------------------------------------------------------
# Fixture
# ---------------------------------------------------------------------------

@pytest.fixture
def sqs(monkeypatch):
    """Yield a mock boto3 SQS client; replaces _sqs() for every call in this test."""
    mock = MagicMock()
    monkeypatch.setattr(_sqs_mod, "_sqs", lambda: mock)
    return mock


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _body(sqs_mock) -> dict:
    """Parse the MessageBody from the most recent send_message call."""
    return json.loads(sqs_mock.send_message.call_args[1]["MessageBody"])


def _queue(sqs_mock) -> str:
    return sqs_mock.send_message.call_args[1]["QueueUrl"]


# ---------------------------------------------------------------------------
# enqueue_collection_item
# ---------------------------------------------------------------------------

class TestEnqueueCollectionItem:

    def test_targets_collection_queue(self, sqs):
        enqueue_collection_item("req-1", "C1234-PROV")
        assert _queue(sqs) == config.collection_queue_url

    def test_type_is_collection(self, sqs):
        enqueue_collection_item("req-1", "C1234-PROV")
        assert _body(sqs)["type"] == "collection"

    def test_request_id_propagated(self, sqs):
        enqueue_collection_item("my-req-id", "C1234-PROV")
        assert _body(sqs)["request_id"] == "my-req-id"

    def test_collection_id_propagated(self, sqs):
        enqueue_collection_item("req-1", "C9999-TESTPROV")
        assert _body(sqs)["collection_id"] == "C9999-TESTPROV"

    def test_after_propagated(self, sqs):
        enqueue_collection_item("req-1", "C1-PROV", after="2024-01-01T00:00:00Z")
        assert _body(sqs)["after"] == "2024-01-01T00:00:00Z"

    def test_before_propagated(self, sqs):
        enqueue_collection_item("req-1", "C1-PROV", before="2024-12-31T23:59:59Z")
        assert _body(sqs)["before"] == "2024-12-31T23:59:59Z"

    def test_after_and_before_null_when_omitted(self, sqs):
        enqueue_collection_item("req-1", "C1-PROV")
        body = _body(sqs)
        assert body["after"] is None
        assert body["before"] is None

    def test_body_is_valid_json_string(self, sqs):
        enqueue_collection_item("req-1", "C1-PROV")
        raw = sqs.send_message.call_args[1]["MessageBody"]
        assert isinstance(json.loads(raw), dict)


# ---------------------------------------------------------------------------
# enqueue_page_item
# ---------------------------------------------------------------------------

class TestEnqueuePageItem:

    def test_targets_intermediate_queue(self, sqs):
        enqueue_page_item("req-1", "C1234-PROV", offset=0, limit=10000)
        assert _queue(sqs) == config.intermediate_queue_url

    def test_type_is_granule_page(self, sqs):
        enqueue_page_item("req-1", "C1234-PROV", offset=0, limit=10000)
        assert _body(sqs)["type"] == "granule-page"

    def test_offset_propagated(self, sqs):
        enqueue_page_item("req-1", "C1-PROV", offset=20000, limit=10000)
        assert _body(sqs)["offset"] == 20000

    def test_limit_propagated(self, sqs):
        enqueue_page_item("req-1", "C1-PROV", offset=0, limit=5000)
        assert _body(sqs)["limit"] == 5000

    def test_after_propagated(self, sqs):
        enqueue_page_item("req-1", "C1-PROV", offset=0, limit=100, after="2024-06-01T00:00:00Z")
        assert _body(sqs)["after"] == "2024-06-01T00:00:00Z"

    def test_after_and_before_null_when_omitted(self, sqs):
        enqueue_page_item("req-1", "C1-PROV", offset=0, limit=100)
        body = _body(sqs)
        assert body["after"] is None
        assert body["before"] is None


# ---------------------------------------------------------------------------
# publish_concept_update
# ---------------------------------------------------------------------------

class TestPublishConceptUpdate:

    def test_targets_indexer_queue(self, sqs):
        publish_concept_update("V1234-PROV", 3, "req-1")
        assert _queue(sqs) == config.indexer_queue_url

    def test_does_not_target_intermediate_queue(self, sqs):
        publish_concept_update("V1234-PROV", 3, "req-1")
        assert _queue(sqs) != config.intermediate_queue_url

    def test_action_is_concept_update(self, sqs):
        publish_concept_update("V1234-PROV", 3, "req-1")
        assert _body(sqs)["action"] == "concept-update"

    def test_concept_id_key_is_hyphenated(self, sqs):
        publish_concept_update("V1234-PROV", 3, "req-1")
        body = _body(sqs)
        assert "concept-id" in body, "key must be 'concept-id' (hyphenated) to match CMR indexer contract"
        assert "concept_id" not in body

    def test_revision_id_key_is_hyphenated(self, sqs):
        publish_concept_update("V1234-PROV", 3, "req-1")
        body = _body(sqs)
        assert "revision-id" in body, "key must be 'revision-id' (hyphenated) to match CMR indexer contract"
        assert "revision_id" not in body

    def test_concept_id_value_correct(self, sqs):
        publish_concept_update("G9876-TESTPROV", 5, "req-1")
        assert _body(sqs)["concept-id"] == "G9876-TESTPROV"

    def test_revision_id_value_correct(self, sqs):
        publish_concept_update("G9876-TESTPROV", 42, "req-1")
        assert _body(sqs)["revision-id"] == 42
