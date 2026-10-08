"""Granule jobs, each on a daemon thread holding the job's lease. The cursor is saved on
the job after every window or page, so a restart resumes there.

- Provider jobs walk each provider's *_GRANULES table in id windows, like bootstrap.
  Rows are dispatched by their own deleted flag; a tombstone always has a higher id
  than the revisions before it, so it lands last.
- Collection jobs page one collection by concept_id.
"""
import logging
import threading
import time
from typing import Optional

from app import leases
from app.config import config
from app.db import db_client
from app.db.dynamo import job_store
from app.es.health import check_all_es_health
from app.throttler.worker import throttler

logger = logging.getLogger(__name__)

# So a burst of scans can't exhaust the Oracle pool that API requests share.
_MAX_CONCURRENT_SCANS = 5
_scan_slots = threading.Semaphore(_MAX_CONCURRENT_SCANS)


def _should_stop(request_id: str) -> bool:
    return throttler.is_job_cancelled(request_id) or throttler.stop_event.is_set()


def _acquire_scan_slot(request_id: str) -> bool:
    """False if cancelled or stopped while waiting; on True the caller must release the slot."""
    while not _scan_slots.acquire(timeout=1.0):
        if _should_stop(request_id):
            return False
    return True


def _dedup_latest_revision(rows: list[tuple]) -> list[tuple]:
    """Keep the max-revision row per concept_id. Only within one window: revisions far
    apart in id space are still dispatched more than once."""
    latest: dict[str, tuple] = {}
    for row in rows:
        if row[0] not in latest or row[1] > latest[row[0]][1]:
            latest[row[0]] = row
    return list(latest.values())


def _wait_for_green_or_signal(request_id: str) -> bool:
    """True once both ES clusters are green; False if cancelled or shutting down."""
    paused = False
    while not _should_stop(request_id):
        health = check_all_es_health()
        if health["overall"] == "green":
            if paused:
                logger.info({"event": "dispatch_resumed_es_green", "request_id": request_id})
            return True
        logger.warning({"event": "dispatch_paused_es_not_green", "request_id": request_id, "health": health})
        paused = True
        throttler.stop_event.wait(10.0)
    return False


def _log_stopped(request_id: str) -> None:
    """An interrupted (SIGTERM) job is restarted once its lease lapses; a cancelled one isn't."""
    event = "scan_interrupted" if throttler.stop_event.is_set() else "scan_cancelled"
    logger.info({"event": event, "request_id": request_id})


def _dispatch(request_id: str, records: list[tuple]) -> bool:
    return throttler.dispatch_in_batches(
        records, request_id, on_progress=lambda n: job_store.update_dispatched(request_id, n),
    )


def _scan_provider(request_id: str, provider_id: str, after: Optional[str], before: Optional[str], start_id: int) -> bool:
    """True once the probe finds nothing more; False if cancelled or stopping."""
    logger.info({"event": "provider_scan_started", "request_id": request_id, "provider_id": provider_id, "start_id": start_id})
    # Probe only at the start and after an empty window: a dated probe can't use the
    # PK min/max path, so it's costly.
    next_id: Optional[int] = None
    probe_from = start_id
    while _wait_for_green_or_signal(request_id):
        if next_id is None:
            next_id = db_client.find_next_granule_id_in_range(provider_id, probe_from, after, before)
            if next_id is None:
                logger.info({"event": "provider_scan_complete", "request_id": request_id, "provider_id": provider_id})
                return True

        end_id = next_id + config.id_range_chunk_size
        rows = db_client.fetch_granule_id_range_chunk(provider_id, next_id, end_id, after, before)
        if rows:
            if not _dispatch(request_id, _dedup_latest_revision(rows)):
                return False
            next_id = end_id
        else:
            probe_from, next_id = end_id, None
        job_store.update_scan_cursor(request_id, end_id, provider_id=provider_id)
    return False


def _scan_providers(job: dict) -> bool:
    request_id = job["job_id"]
    done = set(job.get("providers_done") or [])
    for provider_id in job["providers_requested"]:
        if provider_id in done:
            continue
        start_id = (job.get("scan_cursor") or 0) if job.get("scan_provider") == provider_id else 0
        if not _scan_provider(request_id, provider_id, job.get("after"), job.get("before"), start_id):
            return False
        job_store.finish_provider(request_id, provider_id)
    return True


def _scan_collection(job: dict) -> bool:
    request_id, collection_id = job["job_id"], job["collection_id"]
    start_after = job.get("scan_cursor")
    logger.info({"event": "collection_scan_started", "request_id": request_id, "collection_id": collection_id, "resume_after": start_after})
    for page_end, chunk in db_client.stream_granule_ids_paged(
        collection_id,
        chunk_size=config.stream_chunk_size,
        after=job.get("after"),
        before=job.get("before"),
        start_after_concept_id=start_after,
    ):
        if not _wait_for_green_or_signal(request_id) or not _dispatch(request_id, chunk):
            return False
        job_store.update_scan_cursor(request_id, page_end)
    return True


def _run(request_id: str) -> None:
    acquired = False
    try:
        waited_since = time.monotonic()
        acquired = _acquire_scan_slot(request_id)
        if not acquired:
            _log_stopped(request_id)
            return
        job = job_store.get_job(request_id)
        if job is None:
            logger.warning({"event": "scan_job_missing", "request_id": request_id})
            return
        logger.info({"event": "scan_running", "request_id": request_id, "slot_wait_seconds": round(time.monotonic() - waited_since, 1)})

        scan = _scan_collection if job["concept_type"] == "granules-by-collection" else _scan_providers
        if scan(job):
            job_store.mark_job(request_id, "completed")
            logger.info({"event": "scan_complete", "request_id": request_id})
        else:
            _log_stopped(request_id)
    except Exception as exc:
        logger.error({"event": "scan_error", "request_id": request_id, "error": str(exc)})
        try:
            job_store.mark_job(request_id, "failed")
        except Exception as mark_exc:
            logger.error({"event": "scan_mark_failed_error", "request_id": request_id, "error": str(mark_exc)})
    finally:
        if acquired:
            _scan_slots.release()
        leases.release(request_id)


def start_scan(request_id: str) -> threading.Thread:
    """Run a granule job from its cursor on a daemon thread. The lease is held from here,
    so a job still waiting for a slot isn't restarted elsewhere."""
    leases.hold(request_id)
    thread = threading.Thread(target=_run, args=(request_id,), name=f"scan-{request_id}", daemon=True)
    try:
        thread.start()
    except Exception:
        leases.release(request_id)
        raise
    logger.info({"event": "scan_started", "request_id": request_id})
    return thread
