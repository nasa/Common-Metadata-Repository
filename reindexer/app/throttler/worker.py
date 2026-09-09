import logging
import threading
import time
from datetime import datetime, timezone
from typing import Optional

from app.config import config
from app.db import db_client
from app.db.dynamo import checkpoint_store, job_store
from app.es.health import check_all_es_health, wait_for_green
from app.sqs.client import delete_message, publish_concept_updates_batch, receive_messages
from app.sqs.schemas import CollectionWorkItem, parse_work_item
from app.throttler.token_bucket import TokenBucket

logger = logging.getLogger(__name__)


class _CollectionInterrupted(Exception):
    """Raised mid-collection when stop_event fires (SIGTERM / graceful shutdown).

    _process() skips delete_message on any exception, so raising this keeps the
    SQS collection message on the queue for re-delivery after the task restarts.
    The DynamoDB checkpoint persists, allowing the new task to resume from the
    last successfully dispatched concept_id.
    """


class ThrottlerWorker:
    """Streams granule IDs from Oracle and dispatches concept-update messages to the indexer queue.

    Dequeues CollectionWorkItems from the collection SQS queue.  For each collection,
    opens a single Oracle cursor and streams granule IDs in chunks (keyset pagination),
    writing them directly to the CMR indexer SQS queue in parallel batches.
    A DynamoDB checkpoint is written after each chunk so a SIGTERM/restart resumes
    mid-collection rather than restarting from offset 0.
    """

    def __init__(self) -> None:
        self._stop_event = threading.Event()
        self._token_bucket = TokenBucket(config.rate_per_minute)
        self._thread: Optional[threading.Thread] = None
        self._current_job_id: Optional[str] = None
        self._job_lock = threading.Lock()
        self._cancel_cache = None  # set by main.py via set_cancel_cache()
        self._last_active: Optional[str] = None
        self._last_es_check: float = 0.0
        self._last_es_health: dict = {"overall": "green", "collections": "green", "granules": "green"}

    def set_cancel_cache(self, cache) -> None:
        self._cancel_cache = cache

    def is_job_cancelled(self, job_id: str) -> bool:
        return bool(self._cancel_cache and self._cancel_cache.is_cancelled(job_id))

    @property
    def current_job_id(self) -> Optional[str]:
        with self._job_lock:
            return self._current_job_id

    def set_rate(self, rate_per_minute: float) -> None:
        self._token_bucket.update_rate(rate_per_minute)

    def get_rate(self) -> float:
        return self._token_bucket.current_rate

    def liveness(self) -> dict:
        return {
            "alive": bool(self._thread and self._thread.is_alive()),
            "last_active": self._last_active,
        }

    def token_state(self) -> dict:
        return {
            "rate_per_minute": self._token_bucket.current_rate,
            "tokens_available": self._token_bucket.tokens_available,
        }

    def start(self) -> None:
        if config.stream_chunk_size > config.rate_per_minute:
            raise ValueError(
                f"STREAM_CHUNK_SIZE ({config.stream_chunk_size}) must be <= "
                f"RATE_PER_MINUTE ({config.rate_per_minute}): the token bucket "
                f"can never accumulate enough tokens to unblock a consume() call "
                f"of that size, causing an infinite loop."
            )
        self._thread = threading.Thread(target=self._run, name="throttler", daemon=True)
        self._thread.start()
        logger.info({
            "event": "throttler_started",
            "rate_per_minute": config.rate_per_minute,
            "stream_chunk_size": config.stream_chunk_size,
            "sqs_send_workers": config.sqs_send_workers,
        })

    def stop(self) -> None:
        """Signal the worker to stop and wait for it to finish its current batch."""
        logger.info({"event": "throttler_stopping"})
        self._stop_event.set()
        with self._job_lock:
            job_id = self._current_job_id
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=30)
            if self._thread.is_alive():
                logger.warning({"event": "throttler_thread_did_not_exit"})
        if job_id:
            try:
                job_store.try_mark_interrupted(job_id)
            except Exception as exc:
                logger.warning({"event": "interrupted_job_mark_failed", "error": str(exc)})
        logger.info({"event": "throttler_stopped"})

    # ------------------------------------------------------------------
    # Internal loop
    # ------------------------------------------------------------------

    def _run(self) -> None:
        while not self._stop_event.is_set():
            self._last_active = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

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
                    self._last_es_check = 0.0  # force re-check through the guarded path next iteration
                    logger.info({"event": "dispatch_resumed_es_green"})
                except TimeoutError:
                    logger.error({"event": "es_not_green_wait_timeout"})
                    continue

            try:
                source_queue = config.collection_queue_url
                messages = receive_messages(source_queue, max_messages=10, wait_seconds=5)
            except Exception as exc:
                logger.error({"event": "sqs_receive_error", "error": str(exc)})
                continue

            if not messages:
                continue

            for msg in messages:
                if self._stop_event.is_set():
                    break
                self._process(msg, source_queue)

    def _process(self, msg: dict, queue_url: str) -> None:
        receipt = msg["ReceiptHandle"]
        try:
            item = parse_work_item(msg["Body"])
        except Exception as exc:
            logger.error({"event": "work_item_parse_error", "error": str(exc), "body": msg.get("Body")})
            delete_message(queue_url, receipt)
            return

        if self._cancel_cache and self._cancel_cache.is_cancelled(item.request_id):
            logger.info({"event": "work_item_skipped_cancelled", "request_id": item.request_id})
            delete_message(queue_url, receipt)
            return

        with self._job_lock:
            self._current_job_id = item.request_id

        try:
            if isinstance(item, CollectionWorkItem):
                self._handle_collection(item)
            else:
                logger.error({"event": "unknown_work_item_type", "type": item.type, "request_id": item.request_id})
        except _CollectionInterrupted:
            # Graceful shutdown mid-collection: keep SQS message on queue for re-delivery.
            # The DynamoDB checkpoint was already written; the next task picks up from there.
            logger.info({
                "event": "collection_interrupted",
                "request_id": item.request_id,
                "collection_id": getattr(item, "collection_id", None),
            })
            return  # skip delete_message
        except Exception as exc:
            logger.error({
                "event": "work_item_processing_error",
                "error": str(exc),
                "request_id": item.request_id,
                "type": item.type,
            })
            return  # leave message on queue so it can be retried / go to DLQ
        finally:
            with self._job_lock:
                self._current_job_id = None

        delete_message(queue_url, receipt)

    def _handle_collection(self, item: CollectionWorkItem) -> None:
        """Stream all granule IDs for a collection and dispatch them directly to the indexer queue.

        Uses keyset pagination (stream_granule_ids) so Oracle cost is O(chunk) not O(n^2).
        Writes a DynamoDB checkpoint after each successfully dispatched chunk so a
        SIGTERM/restart resumes from the last concept_id rather than offset 0.

        Raises _CollectionInterrupted when stop_event fires mid-stream so that _process()
        skips delete_message, leaving the SQS collection message for re-delivery.
        """
        # Resume from checkpoint if one exists (previous run was interrupted mid-collection)
        ckpt = checkpoint_store.get_collection_checkpoint(item.request_id, item.collection_id)
        start_after = ckpt["last_concept_id"] if ckpt else None
        total_dispatched = ckpt["granules_dispatched"] if ckpt else 0
        chunks_dispatched = ckpt["chunks_dispatched"] if ckpt else 0
        collection_total = 0  # granules dispatched in this run (post-checkpoint)

        logger.info({
            "event": "collection_streaming_start",
            "request_id": item.request_id,
            "collection_id": item.collection_id,
            "resume_after": start_after,
            "prior_dispatched": total_dispatched,
        })

        for chunk in db_client.stream_granule_ids(
            item.collection_id,
            chunk_size=config.stream_chunk_size,
            after=item.after,
            before=item.before,
            start_after_concept_id=start_after,
        ):
            if self.is_job_cancelled(item.request_id):
                # Cancelled: return normally → SQS message deleted, checkpoint left for cleanup
                logger.info({"event": "collection_streaming_cancelled", "request_id": item.request_id})
                return

            # Block until the token bucket allows this chunk, or stop/cancel fires.
            if not self._token_bucket.consume(
                len(chunk),
                stop_event=self._stop_event,
                cancel_fn=lambda: self.is_job_cancelled(item.request_id),
            ):
                if self._stop_event.is_set():
                    raise _CollectionInterrupted()  # keep SQS message + checkpoint intact
                # cancel_fn fired inside consume()
                logger.info({"event": "collection_streaming_cancelled", "request_id": item.request_id})
                return

            publish_concept_updates_batch(chunk, item.request_id)

            collection_total += len(chunk)
            total_dispatched += len(chunk)
            chunks_dispatched += 1
            last_concept_id = chunk[-1][0]

            # Checkpoint AFTER a successful send so a crash-before-checkpoint just
            # re-dispatches one chunk (indexer idempotency handles duplicates).
            checkpoint_store.write_collection_checkpoint(
                item.request_id,
                item.collection_id,
                last_concept_id,
                total_dispatched,
                chunks_dispatched,
            )
            job_store.update_dispatched(item.request_id, len(chunk))

        # All chunks dispatched — clear checkpoint and record the collection as split.
        # Pass total_dispatched (prior checkpoint + this run) so total_granules_expected
        # in the job record always reflects the full collection count, even on resume.
        checkpoint_store.delete_collection_checkpoint(item.request_id, item.collection_id)
        job_store.increment_collections_split(item.request_id, total_dispatched)

        logger.info({
            "event": "collection_streaming_complete",
            "request_id": item.request_id,
            "collection_id": item.collection_id,
            "collection_total": collection_total,
            "total_dispatched": total_dispatched,
        })

        job_store.try_complete_job(item.request_id)


throttler = ThrottlerWorker()
