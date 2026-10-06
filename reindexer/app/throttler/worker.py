"""Throttler worker for the cmr-reindexer service.

Reads CollectionWorkItems from the collection SQS queue, pages granule IDs
from Oracle using keyset pagination, and dispatches concept-update messages to
the CMR indexer queue at a configurable rate via TokenBucket.  A DynamoDB
checkpoint is written after each chunk so a SIGTERM/restart resumes mid-collection
rather than restarting from offset 0.
"""
import logging
import threading
import time
from dataclasses import dataclass, field
from typing import Callable, Optional

from app.config import config
from app.db import db_client
from app.db.dynamo import checkpoint_store, job_store
from app.es.health import check_all_es_health, wait_for_green
from app.sqs.client import (
    change_message_visibility, delete_message, publish_concept_updates_batch, receive_messages,
)
from app.sqs.schemas import CollectionWorkItem, parse_work_item
from app.throttler.token_bucket import TokenBucket

logger = logging.getLogger(__name__)


# SQS rejects extending a message's visibility past 12 hours from its receive, so
# hand it back a little before that and pick it up again with a fresh receive.
_MAX_MESSAGE_HOLD_SECONDS = 11.5 * 3600


class _CollectionInterrupted(Exception):
    """Raised at a page boundary when stop_event fires (SIGTERM / graceful shutdown)
    or the message's lease is lost.

    _process() then hands the SQS collection message back instead of deleting it,
    so it is redelivered (to this task or another) and resumes from the DynamoDB
    checkpoint of the last successfully dispatched page.
    """


@dataclass
class _MessageLease:
    """The collection message being processed. Its visibility is the work item's
    lease, renewed by the lease keeper. lost: a renewal failed or SQS's 12-hour cap
    is near, so hand the message back at the next page boundary."""
    queue_url: str
    receipt: str
    job_id: str
    received_at: float = field(default_factory=time.monotonic)
    lost: bool = False


class ThrottlerWorker:
    """Streams granule IDs from Oracle and dispatches concept-update messages to the indexer queue.

    Dequeues CollectionWorkItems from the collection SQS queue.  For each collection,
    pages granule IDs from Oracle (keyset pagination by concept_id), writing them
    directly to the CMR indexer SQS queue in parallel batches.
    A DynamoDB checkpoint is written after each page so a SIGTERM/restart resumes
    mid-collection rather than restarting from offset 0.
    """

    def __init__(self) -> None:
        self._stop_event = threading.Event()
        self._token_bucket = TokenBucket(config.rate_per_minute)
        self._thread: Optional[threading.Thread] = None
        self._lease: Optional[_MessageLease] = None
        self._job_lock = threading.Lock()
        self._cancel_cache = None  # set by main.py via set_cancel_cache()
        self._last_es_check: float = 0.0
        self._last_es_health: dict = {"overall": "green", "collections": "green", "granules": "green"}

    def set_cancel_cache(self, cache) -> None:
        self._cancel_cache = cache

    def is_job_cancelled(self, job_id: str) -> bool:
        return bool(self._cancel_cache and self._cancel_cache.is_cancelled(job_id))

    @property
    def current_job_id(self) -> Optional[str]:
        lease = self._lease  # no lock: renewal holds _job_lock across an SQS call
        return lease.job_id if lease else None

    def set_rate(self, rate_per_minute: float) -> None:
        self._token_bucket.update_rate(rate_per_minute)

    def get_rate(self) -> float:
        return self._token_bucket.current_rate

    @property
    def stop_event(self) -> threading.Event:
        """Shutdown signal, shared with other dispatch loops (the id-range scan)."""
        return self._stop_event

    def dispatch_in_batches(
        self,
        records: list[tuple[str, int]],
        request_id: str,
        on_progress: Optional[Callable[[int], None]] = None,
    ) -> bool:
        """Publish records in sub-batches, consuming tokens for each. Sub-batches are
        capped at the current rate (re-read each iteration, so a mid-chunk PUT
        /throttle is honored), since consume(N) can never succeed when N exceeds
        the bucket's capacity.

        on_progress(n) is called after each sub-batch publishes. Returns False as
        soon as consume() reports shutdown or cancellation, else True.
        """
        i = 0
        while i < len(records):
            sub_size = max(1, int(self._token_bucket.current_rate))
            sub = records[i:i + sub_size]
            if not self._token_bucket.consume(
                len(sub), stop_event=self._stop_event, cancel_fn=lambda: self.is_job_cancelled(request_id),
            ):
                return False
            publish_concept_updates_batch(sub, request_id)
            if on_progress is not None:
                on_progress(len(sub))
            i += len(sub)
        return True

    def renew_message_lease(self) -> None:
        """Extend the current message's visibility by another lease. Holds _job_lock
        across the call, so it can't land after _process releases the message.
        A lost lease is still renewed, to keep the message hidden until handed back."""
        with self._job_lock:
            lease = self._lease
            if lease is None:
                return
            if not lease.lost and time.monotonic() - lease.received_at > _MAX_MESSAGE_HOLD_SECONDS:
                lease.lost = True
                logger.info({"event": "message_lease_max_hold", "request_id": lease.job_id})
            try:
                change_message_visibility(lease.queue_url, lease.receipt, config.lease_minutes * 60)
            except Exception as exc:
                lease.lost = True
                logger.warning({"event": "message_lease_lost", "request_id": lease.job_id, "error": str(exc)})

    def is_alive(self) -> bool:
        return bool(self._thread and self._thread.is_alive())

    def start(self) -> None:
        self._thread = threading.Thread(target=self._run, name="throttler", daemon=True)
        self._thread.start()
        logger.info({"event": "throttler_started"})

    def stop(self) -> None:
        """Signal the worker to stop and wait for it to finish its current batch."""
        if self._stop_event.is_set():
            return
        logger.info({"event": "throttler_stopping"})
        self._stop_event.set()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=30)
            if self._thread.is_alive():
                logger.warning({"event": "throttler_thread_did_not_exit"})
        logger.info({"event": "throttler_stopped"})

    # ------------------------------------------------------------------
    # Internal loop
    # ------------------------------------------------------------------

    def _run(self) -> None:
        while not self._stop_event.is_set():
            # Gate: check ES at most once per 30s to avoid hammering the clusters at idle
            now = time.monotonic()
            if now - self._last_es_check >= 30.0:
                try:
                    self._last_es_health = check_all_es_health()
                except Exception as exc:
                    logger.warning({"event": "es_health_check_failed", "error": str(exc)})
                self._last_es_check = now
            health = self._last_es_health
            if health["overall"] != "green":
                logger.warning({
                    "event": "dispatch_paused_es_not_green",
                    "health": health,
                })
                try:
                    wait_for_green(
                        poll_interval_seconds=10.0,
                        timeout_seconds=300.0,
                        stop_event=self._stop_event,
                    )
                except TimeoutError:
                    logger.error({"event": "es_not_green_wait_timeout"})
                    continue
                if self._stop_event.is_set():
                    continue  # shutting down — don't log a false "resumed" or block in receive_messages
                self._last_es_check = 0.0  # force re-check through the guarded path next iteration
                logger.info({"event": "dispatch_resumed_es_green"})

            try:
                source_queue = config.collection_queue_url
                # One at a time, so no received message waits hidden behind the current one.
                messages = receive_messages(
                    source_queue, visibility_timeout=config.lease_minutes * 60, max_messages=1, wait_seconds=5,
                )
            except Exception as exc:
                logger.error({"event": "sqs_receive_error", "error": str(exc)})
                continue

            if messages:
                self._process(messages[0], source_queue)

    def _process(self, msg: dict, queue_url: str) -> None:
        receipt = msg["ReceiptHandle"]
        try:
            item = parse_work_item(msg["Body"])
        except Exception as exc:
            logger.error({"event": "work_item_parse_error", "error": str(exc), "body": msg.get("Body")})
            try:
                delete_message(queue_url, receipt)
            except Exception as del_exc:
                logger.warning({"event": "delete_message_failed", "error": str(del_exc)})
            return

        if self._cancel_cache and self._cancel_cache.is_cancelled(item.request_id):
            logger.info({"event": "work_item_skipped_cancelled", "request_id": item.request_id})
            delete_message(queue_url, receipt)
            return

        with self._job_lock:
            self._lease = _MessageLease(queue_url, receipt, item.request_id)

        sent_ms = msg.get("Attributes", {}).get("SentTimestamp")
        queue_wait_seconds = round(time.time() - int(sent_ms) / 1000, 1) if sent_ms else None
        interrupted = False
        try:
            self._handle_collection(item, queue_wait_seconds)
        except _CollectionInterrupted:
            # The DynamoDB checkpoint was already written; whoever receives the
            # message next picks up from there.
            logger.info({
                "event": "collection_interrupted",
                "request_id": item.request_id,
                "collection_id": getattr(item, "collection_id", None),
            })
            interrupted = True
        except Exception as exc:
            logger.error({
                "event": "work_item_processing_error",
                "error": str(exc),
                "request_id": item.request_id,
                "type": item.type,
            })
            return  # leave message on queue; retried once its lease lapses, or goes to DLQ
        finally:
            with self._job_lock:
                self._lease = None

        try:
            if interrupted:
                # Hand the message back now rather than when its lease lapses.
                change_message_visibility(queue_url, receipt, 0)
            else:
                delete_message(queue_url, receipt)
        except Exception as exc:
            logger.warning({"event": "settle_message_failed", "interrupted": interrupted, "error": str(exc)})

    def _handle_collection(self, item: CollectionWorkItem, queue_wait_seconds: Optional[float] = None) -> None:
        """Stream all granule IDs for a collection and dispatch them directly to the indexer queue.

        Uses keyset pagination (stream_granule_ids_paged) so Oracle cost is O(page) not O(n^2).
        Writes a DynamoDB checkpoint after each page — including pages whose rows were
        all filtered out by after/before — so a SIGTERM/restart resumes from the last
        page boundary rather than offset 0.

        Raises _CollectionInterrupted when stop_event fires or the message's lease is
        lost, so that _process() hands the message back for re-delivery.
        """
        # Resume from checkpoint if one exists (previous run was interrupted mid-collection)
        ckpt = checkpoint_store.get_collection_checkpoint(item.request_id, item.collection_id)
        start_after = ckpt["last_concept_id"] if ckpt else None
        total_dispatched = ckpt["granules_dispatched"] if ckpt else 0
        chunks_dispatched = ckpt["chunks_dispatched"] if ckpt else 0
        collection_total = 0  # granules dispatched in this run (post-checkpoint)

        start_event = {
            "event": "collection_streaming_start",
            "request_id": item.request_id,
            "collection_id": item.collection_id,
            "resume_after": start_after,
            "prior_dispatched": total_dispatched,
        }
        if not ckpt:
            # Time this work item spent on the collection queue before its first run.
            start_event["queue_wait_seconds"] = queue_wait_seconds
        logger.info(start_event)

        for page_end, chunk in db_client.stream_granule_ids_paged(
            item.collection_id,
            chunk_size=config.stream_chunk_size,
            after=item.after,
            before=item.before,
            start_after_concept_id=start_after,
        ):
            if self._stop_event.is_set() or (self._lease and self._lease.lost):
                raise _CollectionInterrupted()
            if self.is_job_cancelled(item.request_id) or not self.dispatch_in_batches(
                chunk, item.request_id,
                on_progress=lambda n: job_store.update_dispatched(item.request_id, n),
            ):
                if self._stop_event.is_set():
                    raise _CollectionInterrupted()
                # Cancelled: return normally → SQS message deleted, checkpoint left for cleanup
                logger.info({"event": "collection_streaming_cancelled", "request_id": item.request_id})
                return

            collection_total += len(chunk)
            total_dispatched += len(chunk)
            chunks_dispatched += 1

            # Checkpoint AFTER a successful send so a crash-before-checkpoint just
            # re-dispatches one page (indexer idempotency handles duplicates).
            checkpoint_store.write_collection_checkpoint(
                item.request_id,
                item.collection_id,
                page_end,
                total_dispatched,
                chunks_dispatched,
            )

        # All chunks dispatched — clear checkpoint and record the collection as split.
        checkpoint_store.delete_collection_checkpoint(item.request_id, item.collection_id)
        provider_id = item.collection_id.split("-", 1)[1]
        job_store.increment_collections_split(item.request_id, provider_id)

        logger.info({
            "event": "collection_streaming_complete",
            "request_id": item.request_id,
            "collection_id": item.collection_id,
            "collection_total": collection_total,
            "total_dispatched": total_dispatched,
        })

        job_store.try_complete_job(item.request_id)


throttler = ThrottlerWorker()
