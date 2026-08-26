import functools
import json
import logging
from typing import Optional

import boto3

from app.config import config
from app.sqs.schemas import CollectionWorkItem, GranulePageWorkItem

logger = logging.getLogger(__name__)


@functools.lru_cache(maxsize=1)
def _sqs():
    return boto3.client(
        "sqs",
        region_name=config.aws_region,
        endpoint_url=config.sqs_endpoint_url,        # None → real AWS SQS
        aws_access_key_id=config.aws_access_key_id,  # None → credential chain (IAM task role)
        aws_secret_access_key=config.aws_secret_access_key,
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


def enqueue_page_item(
    request_id: str,
    collection_id: str,
    offset: int,
    limit: int,
    after: Optional[str] = None,
    before: Optional[str] = None,
) -> None:
    item = GranulePageWorkItem(
        request_id=request_id,
        collection_id=collection_id,
        offset=offset,
        limit=limit,
        after=after,
        before=before,
    )
    _sqs().send_message(QueueUrl=config.intermediate_queue_url, MessageBody=item.to_json())
    logger.info({
        "event": "enqueued_page_item",
        "request_id": request_id,
        "collection_id": collection_id,
        "offset": offset,
        "limit": limit,
    })


def publish_concept_update(concept_id: str, revision_id: int, request_id: str) -> None:
    """Send a concept-update message to the CMR indexer queue."""
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
