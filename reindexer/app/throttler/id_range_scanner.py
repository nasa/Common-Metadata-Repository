"""Id-range provider scan — dispatches a whole provider's granules on a dedicated
thread, bypassing the SQS collection queue and ThrottlerWorker's polling loop.
Still rate-limited: publishes via throttler.dispatch_in_batches, sharing the same
TokenBucket as the per-collection sweep.

Walks the provider's *_GRANULES table in bounded id windows, since no index
covers PARENT_COLLECTION_ID + REVISION_DATE. Resume cursor: next_start_id on the
job record.

Runs on its own daemon thread: the lease keeper's restart has no BackgroundTasks
instance, and a scan can run for hours. Holds the job's lease from start to finish.
"""
import logging
import threading
from typing import Optional

from app import leases
from app.config import config
from app.db import db_client
from app.db.dynamo import job_store
from app.es.health import check_all_es_health
from app.throttler.worker import throttler

logger = logging.getLogger(__name__)

# Caps concurrent scan threads so a burst of restarts/requests can't flood the
# shared Oracle pool (oracle_pool_max) that ordinary API traffic also depends on.
_MAX_CONCURRENT_SCANS = 5
_scan_slots = threading.Semaphore(_MAX_CONCURRENT_SCANS)


def _should_stop(request_id: str) -> bool:
    return throttler.is_job_cancelled(request_id) or throttler.stop_event.is_set()


def _acquire_scan_slot(request_id: str) -> bool:
    """Block until a slot is free. Returns False if cancelled/stopped while
    waiting — otherwise True, and the caller must release the slot when done."""
    while not _scan_slots.acquire(timeout=1.0):
        if _should_stop(request_id):
            return False
    return True


def _dedup_latest_revision(rows: list[tuple[str, int]]) -> list[tuple[str, int]]:
    """Keep only the max revision_id per concept_id within one fetched chunk.

    fetch_granule_id_range_chunk filters deleted=0 per-row, not aggregated per
    concept_id, so the same concept can appear more than once in a chunk if it was
    revised more than once inside this id window. This doesn't catch revisions far
    apart in id-space, but kills the common case of revisions clustered close
    together, at zero extra DB cost.
    """
    latest: dict[str, int] = {}
    for concept_id, revision_id in rows:
        if revision_id > latest.get(concept_id, -1):
            latest[concept_id] = revision_id
    return list(latest.items())


def _wait_for_green_or_signal(request_id: str) -> bool:
    """Block until both ES clusters are green (True), or until the job is
    cancelled or the service is shutting down (False)."""
    while not _should_stop(request_id):
        health = check_all_es_health()
        if health["overall"] == "green":
            return True
        logger.warning({"event": "dispatch_paused_es_not_green", "request_id": request_id, "health": health})
        throttler.stop_event.wait(10.0)
    return False


def _log_stopped(request_id: str) -> None:
    """Log why a scan stopped: interrupted (SIGTERM; its lease lapses and another
    task restarts it) or cancelled (operator action, not restarted)."""
    event = "id_range_scan_interrupted" if throttler.stop_event.is_set() else "id_range_scan_cancelled"
    logger.info({"event": event, "request_id": request_id})


def _run(
    request_id: str,
    provider_id: str,
    after: Optional[str],
    before: Optional[str],
    start_id: int,
) -> None:
    acquired = False
    try:
        acquired = _acquire_scan_slot(request_id)
        if not acquired:
            _log_stopped(request_id)
            return

        # Probe only at the start and after an empty window; after a window with
        # data, just advance by one window. With `after` set, the probe can't use the
        # PK min/max path and reads every matching row.
        next_id: Optional[int] = None  # None → probe from probe_from first
        probe_from = start_id
        while _wait_for_green_or_signal(request_id):
            if next_id is None:
                next_id = db_client.find_next_granule_id_in_range(provider_id, probe_from, after)
                if next_id is None:
                    # try_complete_job relies on counters this scan never increments.
                    job_store.mark_job(request_id, "completed")
                    logger.info({"event": "id_range_scan_complete", "request_id": request_id, "provider_id": provider_id})
                    return

            end_id = next_id + config.id_range_chunk_size
            rows = db_client.fetch_granule_id_range_chunk(provider_id, next_id, end_id, after, before)
            if rows:
                if not throttler.dispatch_in_batches(
                    _dedup_latest_revision(rows),
                    request_id,
                    on_progress=lambda n: job_store.update_dispatched(request_id, n),
                ):
                    break
                next_id = end_id
            else:
                probe_from, next_id = end_id, None
            job_store.update_id_range_progress(request_id, end_id)

        _log_stopped(request_id)
    except Exception as exc:
        logger.error({"event": "id_range_scan_error", "request_id": request_id, "provider_id": provider_id, "error": str(exc)})
        try:
            job_store.mark_job(request_id, "failed")
        except Exception as mark_exc:
            logger.error({"event": "id_range_scan_mark_failed_error", "request_id": request_id, "error": str(mark_exc)})
    finally:
        if acquired:
            _scan_slots.release()
        leases.release(request_id)


def start_id_range_scan(
    request_id: str,
    provider_id: str,
    after: Optional[str],
    before: Optional[str],
    start_id: int = 0,
) -> threading.Thread:
    """Spawn the id-range scan on a dedicated daemon thread. Called from
    enqueue_provider, for both the initial run and a lease keeper restart."""
    leases.hold(request_id)
    thread = threading.Thread(
        target=_run,
        args=(request_id, provider_id, after, before, start_id),
        name=f"id-range-scan-{request_id}",
        daemon=True,
    )
    thread.start()
    logger.info({
        "event": "id_range_scan_started",
        "request_id": request_id,
        "provider_id": provider_id,
        "start_id": start_id,
    })
    return thread

