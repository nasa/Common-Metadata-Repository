import functools
import json
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
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
        config=Config(max_pool_connections=config.sqs_send_workers),
    )


def enqueue_collection_item(
    request_id: str,
    collection_id: str,
    after: Optional[str] = None,
    before: Optional[str] = None,
) -> None:
    item = CollectionWorkItem(
        request_id=request_id, collection_id=collection_id, after=after, before=before
    )
    _sqs().send_message(QueueUrl=config.collection_queue_url, MessageBody=item.to_json())
    logger.info({
        "event": "enqueued_collection_item",
        "request_id": request_id,
        "collection_id": collection_id,
    })



def publish_concept_update(concept_id: str, revision_id: int, request_id: str) -> None:
    """Send a single concept-update message to the CMR indexer queue."""
    msg = json.dumps({
        "action": "concept-update",
        "concept-id": concept_id,
        "revision-id": revision_id,
    })
    _sqs().send_message(QueueUrl=config.indexer_queue_url, MessageBody=msg)
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


def publish_concept_updates_batch(records: list[tuple[str, int]], request_id: str) -> None:
    """Send concept-update messages to the CMR indexer queue in parallel batches of 10.

    SQS send_message_batch accepts up to 10 messages per call.  Batches are sent
    concurrently via a thread pool so the HTTP round-trip cost is O(1 pool round)
    rather than O(N serial calls).  Raises RuntimeError if any batch fails.
    """
    batches = [
        [
            {
                "Id": str(j),
                "MessageBody": json.dumps({
                    "action": "concept-update",
                    "concept-id": concept_id,
                    "revision-id": revision_id,
                }),
            }
            for j, (concept_id, revision_id) in enumerate(records[i:i + _BATCH_SIZE])
        ]
        for i in range(0, len(records), _BATCH_SIZE)
    ]

    with ThreadPoolExecutor(max_workers=config.sqs_send_workers) as pool:
        futures = [pool.submit(_send_one_sqs_batch, batch) for batch in batches]
    # Executor has shut down — all futures are done. Collect every error so none are silently
    # dropped (raising inside as_completed would exit the loop early, swallowing later failures).
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
        "event": "concept_updates_batch_published",
        "request_id": request_id,
        "count": len(records),
    })


def get_queue_depth(queue_url: str) -> int:
    attrs = _sqs().get_queue_attributes(
        QueueUrl=queue_url,
        AttributeNames=["ApproximateNumberOfMessages", "ApproximateNumberOfMessagesNotVisible"],
    ).get("Attributes", {})
    return int(attrs.get("ApproximateNumberOfMessages", 0)) + int(attrs.get("ApproximateNumberOfMessagesNotVisible", 0))


def receive_messages(queue_url: str, max_messages: int = 10, wait_seconds: int = 5) -> list[dict]:
    return _sqs().receive_message(
        QueueUrl=queue_url,
        MaxNumberOfMessages=max_messages,
        WaitTimeSeconds=wait_seconds,
    ).get("Messages", [])


def delete_message(queue_url: str, receipt_handle: str) -> None:
    _sqs().delete_message(QueueUrl=queue_url, ReceiptHandle=receipt_handle)
