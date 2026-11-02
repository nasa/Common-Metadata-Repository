"""Publishes indexer events (concept-update / concept-delete) to the CMR indexer queue."""
import functools
import json
import logging
from concurrent.futures import ThreadPoolExecutor

import boto3
from botocore.config import Config

from app.config import config

logger = logging.getLogger(__name__)


@functools.lru_cache(maxsize=1)
def _sqs():
    return boto3.client(
        "sqs",
        region_name=config.aws_region,
        endpoint_url=config.sqs_endpoint_url,
        aws_access_key_id=config.aws_access_key_id,
        aws_secret_access_key=config.aws_secret_access_key,
        # The shared send pool, plus headroom for single sends and /status.
        config=Config(max_pool_connections=config.sqs_send_workers + 10),
    )


@functools.lru_cache(maxsize=1)
def _send_pool() -> ThreadPoolExecutor:
    """Shared by every dispatcher, so concurrent sends never exceed sqs_send_workers."""
    return ThreadPoolExecutor(max_workers=config.sqs_send_workers, thread_name_prefix="sqs-send")


def _indexer_event(concept_id: str, revision_id: int, deleted: int = 0) -> str:
    action = "concept-delete" if deleted else "concept-update"
    return json.dumps({"action": action, "concept-id": concept_id, "revision-id": revision_id})


def publish_concept_update(concept_id: str, revision_id: int, request_id: str) -> None:
    _sqs().send_message(QueueUrl=config.indexer_queue_url, MessageBody=_indexer_event(concept_id, revision_id))
    logger.debug({
        "event": "concept_update_published",
        "request_id": request_id,
        "concept_id": concept_id,
        "revision_id": revision_id,
    })


_BATCH_SIZE = 10


def _send_one_sqs_batch(entries: list[dict]) -> None:
    """send_message_batch reports partial failure instead of raising; raise on it."""
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
    """One event per (concept_id, revision_id[, deleted]) record, sent in parallel batches
    of 10 (the SQS limit). Raises RuntimeError if any batch fails."""
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
