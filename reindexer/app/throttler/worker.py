import logging
import threading
import time
from datetime import datetime, timezone
from typing import Optional

from app.config import config
from app.db import db_client
from app.db.dynamo import job_store
from app.es.health import check_all_es_health, wait_for_green
from app.sqs.client import delete_message, enqueue_page_item, publish_concept_update, receive_messages
from app.sqs.schemas import CollectionWorkItem, GranulePageWorkItem, parse_work_item
from app.throttler.token_bucket import TokenBucket

logger = logging.getLogger(__name__)


class ThrottlerWorker:
    """Polls the intermediate SQS queue and dispatches work."""

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
        self._thread = threading.Thread(target=self._run, name="throttler", daemon=True)
        self._thread.start()
        logger.info({
            "event": "throttler_started",
            "rate_per_minute": config.rate_per_minute,
            "chunk_size": config.chunk_size,
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
                job_store.mark_job(job_id, "interrupted")
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
                # Prioritise granule pages over collection splitting so dispatch
                # is never starved by a flood of incoming collection items.
                source_queue = config.intermediate_queue_url
                messages = receive_messages(source_queue, max_messages=10, wait_seconds=2)
                if not messages:
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
            elif isinstance(item, GranulePageWorkItem):
                self._handle_granule_page(item)
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
        count = db_client.get_granule_count(item.collection_id, after=item.after, before=item.before)
        pages = max(1, (count + config.chunk_size - 1) // config.chunk_size) if count > 0 else 0
        job_store.increment_collections_split(item.request_id, count)
        logger.info({
            "event": "collection_splitting",
            "request_id": item.request_id,
            "collection_id": item.collection_id,
            "granule_count": count,
            "pages": pages,
        })

        offset = 0
        while offset < count:
            enqueue_page_item(
                request_id=item.request_id,
                collection_id=item.collection_id,
                offset=offset,
                limit=config.chunk_size,
                after=item.after,
                before=item.before,
            )
            offset += config.chunk_size

        logger.info({
            "event": "collection_split_complete",
            "request_id": item.request_id,
            "collection_id": item.collection_id,
            "pages_enqueued": pages,
        })
        job_store.try_complete_job(item.request_id)

    def _handle_granule_page(self, item: GranulePageWorkItem) -> None:
        granule_records = db_client.get_granule_ids(
            item.collection_id, item.offset, item.limit, after=item.after, before=item.before
        )
        logger.info({
            "event": "granule_page_fetched",
            "request_id": item.request_id,
            "collection_id": item.collection_id,
            "offset": item.offset,
            "limit": item.limit,
            "count": len(granule_records),
        })

        if not granule_records:
            job_store.try_complete_job(item.request_id)
            return

        job_store.update_heartbeat(item.request_id)
        if self.is_job_cancelled(item.request_id):
            return
        # Rate-limit: block until bucket allows this batch, or shutdown fires
        if not self._token_bucket.consume(len(granule_records), stop_event=self._stop_event):
            return

        for concept_id, revision_id in granule_records:
            publish_concept_update(concept_id, revision_id, item.request_id)

        job_store.update_dispatched(item.request_id, len(granule_records))
        job_store.try_complete_job(item.request_id)


throttler = ThrottlerWorker()
