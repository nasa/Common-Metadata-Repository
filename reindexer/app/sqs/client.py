"""SQS client helpers for the cmr-reindexer service.

Provides functions for enqueuing collection work items, sending single-concept
updates, and publishing concept-update messages to the CMR indexer queue in
parallel batches of up to 10 (the SQS send_message_batch limit).
"""
import functools
import json
import logging
from concurrent.futures import ThreadPoolExecutor
from typing import Optional

import boto3
from botocore.config import Config

from app.config import config
from app.sqs.schemas import CollectionWorkItem

logger = logging.getLogger(__name__)


@functools.lru_cache(maxsize=1)
def _sqs():
    return boto3.client(
        "sqs",
        region_name=config.aws_region,
        endpoint_url=config.sqs_endpoint_url,        # None → real AWS SQS
        aws_access_key_id=config.aws_access_key_id,  # None → credential chain (IAM task role)
        aws_secret_access_key=config.aws_secret_access_key,
        # The shared send pool, plus headroom for receives, lease renewals and /status.
        config=Config(max_pool_connections=config.sqs_send_workers + 10),
    )


@functools.lru_cache(maxsize=1)
def _send_pool() -> ThreadPoolExecutor:
    """Shared by every dispatcher, so concurrent sends never exceed sqs_send_workers."""
    return ThreadPoolExecutor(max_workers=config.sqs_send_workers, thread_name_prefix="sqs-send")


def enqueue_collection_item(
    request_id: str,
    collection_id: str,
    after: Optional[str] = None,
    before: Optional[str] = None,
    include_deleted: bool = False,
) -> None:
    item = CollectionWorkItem(
        request_id=request_id, collection_id=collection_id, after=after, before=before,
        include_deleted=include_deleted,
    )
    _sqs().send_message(QueueUrl=config.collection_queue_url, MessageBody=item.to_json())
    logger.info({
        "event": "enqueued_collection_item",
        "request_id": request_id,
        "collection_id": collection_id,
    })



def _indexer_event(concept_id: str, revision_id: int, deleted: int = 0) -> str:
    action = "concept-delete" if deleted else "concept-update"
    return json.dumps({"action": action, "concept-id": concept_id, "revision-id": revision_id})


def publish_concept_update(concept_id: str, revision_id: int, request_id: str) -> None:
    """Send a single concept-update message to the CMR indexer queue."""
    _sqs().send_message(QueueUrl=config.indexer_queue_url, MessageBody=_indexer_event(concept_id, revision_id))
    logger.debug({
        "event": "concept_update_published",
        "request_id": request_id,
        "concept_id": concept_id,
        "revision_id": revision_id,
    })


_BATCH_SIZE = 10


def _send_one_sqs_batch(entries: list[dict]) -> None:
    """Send one SQS batch (≤ 10 messages).  Raises RuntimeError on partial failure."""
    response = _sqs().send_message_batch(
        QueueUrl=config.indexer_queue_url,
        Entries=entries,
    )
    failed = response.get("Failed", [])
    if failed:
        raise RuntimeError(
            f"SQS batch send partial failure: {len(failed)}/{len(entries)} messages failed — "
            f"{failed[0].get('Code')}: {failed[0].get('Message')}"
        )


def publish_indexer_events_batch(records: list[tuple], request_id: str) -> None:
    """Send an indexer event per (concept_id, revision_id[, deleted]) record, in parallel
    batches of 10 (the send_message_batch limit) on the shared send pool: concept-delete
    for deleted records, else concept-update. Raises RuntimeError if any batch fails.
    """
    batches = [
        [
            {"Id": str(j), "MessageBody": _indexer_event(*record)}
            for j, record in enumerate(records[i:i + _BATCH_SIZE])
        ]
        for i in range(0, len(records), _BATCH_SIZE)
    ]

    futures = [_send_pool().submit(_send_one_sqs_batch, batch) for batch in batches]
    # Wait on every future, so one failure doesn't hide the others.
    errors = []
    for fut in futures:
        try:
            fut.result()
        except Exception as exc:
            errors.append(exc)
    if errors:
        raise RuntimeError(
            f"{len(errors)} of {len(futures)} SQS batch(es) failed; first: {errors[0]}"
        )

    logger.debug({
        "event": "indexer_events_batch_published",
        "request_id": request_id,
        "count": len(records),
    })


def get_queue_counts(queue_url: str) -> dict:
    attrs = _sqs().get_queue_attributes(
        QueueUrl=queue_url,
        AttributeNames=["ApproximateNumberOfMessages", "ApproximateNumberOfMessagesNotVisible"],
    ).get("Attributes", {})
    return {
        "available": int(attrs.get("ApproximateNumberOfMessages", 0)),
        "in_flight": int(attrs.get("ApproximateNumberOfMessagesNotVisible", 0)),
    }


def receive_messages(
    queue_url: str, visibility_timeout: int, max_messages: int = 10, wait_seconds: int = 5,
) -> list[dict]:
    return _sqs().receive_message(
        QueueUrl=queue_url,
        MaxNumberOfMessages=max_messages,
        WaitTimeSeconds=wait_seconds,
        VisibilityTimeout=visibility_timeout,
        AttributeNames=["SentTimestamp"],
    ).get("Messages", [])


def change_message_visibility(queue_url: str, receipt_handle: str, seconds: int) -> None:
    _sqs().change_message_visibility(QueueUrl=queue_url, ReceiptHandle=receipt_handle, VisibilityTimeout=seconds)


def delete_message(queue_url: str, receipt_handle: str) -> None:
    _sqs().delete_message(QueueUrl=queue_url, ReceiptHandle=receipt_handle)
