"""Renews this task's leases, so slow-but-alive work never loses one, and restarts
jobs whose lease lapsed because their owner is gone. Collection work items are
leased by SQS visibility (a lapsed message is simply redelivered); other jobs by
last_heartbeat (app.leases).
"""
import logging
import threading
import time

from app import leases
from app.config import config
from app.db.call_tracker import log_slow_calls
from app.routers import reindex
from app.throttler.worker import throttler

logger = logging.getLogger(__name__)

# Log any Oracle call still running after this long, every tick until it returns.
_SLOW_CALL_SECONDS = 300


def _start_thread(target, job_id: str, *args, **kwargs) -> None:
    thread = threading.Thread(target=target, args=(job_id, *args), kwargs=kwargs, name=f"restart-{job_id}", daemon=True)
    thread.start()


def restart_lapsed_jobs(job_store) -> None:
    """Restart jobs whose lease lapsed. Each restart picks up from the job's
    persisted progress: providers_enqueued for the enqueue loop, next_start_id for
    an id-range scan. A concept type is republished from scratch (idempotent).
    """
    held = leases.held_jobs()
    for job in job_store.find_lapsed_jobs(config.lease_minutes):
        job_id = job["job_id"]
        # Held here means our own renewals have been failing; the work is still running.
        if job_id in held or not job_store.claim_lapsed_job(job_id, job["last_heartbeat"]):
            continue

        concept_type = job.get("concept_type", "")
        logger.info({
            "event": "restarting_lapsed_job", "job_id": job_id, "concept_type": concept_type, "status": job.get("status"),
        })
        after, before = job.get("after"), job.get("before")
        try:
            if concept_type in ("granules", "granules-by-providers") and job.get("providers_requested"):
                _start_thread(
                    reindex.enqueue_providers, job_id, job["providers_requested"], after, before,
                    skip=set(job.get("providers_enqueued") or []),
                )
            elif concept_type == "granules":  # died before it listed the providers
                _start_thread(reindex.enqueue_all_providers, job_id, after, before)
            elif concept_type == "granules-by-provider":
                reindex.enqueue_provider(job_id, job["provider_id"], after, before, start_id=job.get("next_start_id") or 0)
            elif concept_type in reindex.ROUTE_TO_INTERNAL_TYPE:
                _start_thread(reindex.publish_concept_type, job_id, reindex.ROUTE_TO_INTERNAL_TYPE[concept_type], before)
            else:
                # A synchronous request that died before finishing (single concept, collection enqueue)
                job_store.mark_job(job_id, "failed")
        except Exception as exc:
            logger.error({"event": "lapsed_job_restart_error", "job_id": job_id, "error": str(exc)})
            job_store.mark_job(job_id, "failed")


def _tick(job_store, stop_event: threading.Event) -> None:
    # Separate try blocks, so one failing step doesn't skip the others.
    try:
        throttler.renew_message_lease()
    except Exception as exc:
        logger.warning({"event": "message_lease_renew_error", "error": str(exc)})

    # The throttler's current job is leased by its message; renewing its heartbeat
    # too only keeps /jobs heartbeat_stale accurate for it.
    for job_id in (leases.held_jobs() | {throttler.current_job_id}) - {None}:
        try:
            job_store.update_heartbeat(job_id)
        except Exception as exc:
            logger.warning({"event": "lease_renew_error", "job_id": job_id, "error": str(exc)})

    log_slow_calls(_SLOW_CALL_SECONDS)

    # A task that is shutting down leaves restarts to the tasks that aren't.
    if not stop_event.is_set():
        try:
            restart_lapsed_jobs(job_store)
        except Exception as exc:
            logger.error({"event": "lapsed_job_poll_error", "error": str(exc)})


def start_lease_keeper(job_store, stop_event: threading.Event) -> threading.Thread:
    """Run _tick now and then five times per lease, for the life of the process.
    Renewal deliberately ignores stop_event: work can outlive SIGTERM (a worker
    stuck past its join, a BackgroundTask uvicorn waits on), and must stay leased
    until it actually ends."""
    def _run() -> None:
        while True:
            _tick(job_store, stop_event)
            time.sleep(config.lease_minutes * 60 / 5)

    thread = threading.Thread(target=_run, name="lease-keeper", daemon=True)
    thread.start()
    return thread
