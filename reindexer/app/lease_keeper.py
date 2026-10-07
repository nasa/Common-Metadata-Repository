"""Renews the leases this task holds (app.leases) and restarts jobs whose owner is gone."""
import logging
import threading
import time

from app import leases
from app.config import config
from app.db.call_tracker import log_slow_calls
from app.routers import reindex
from app.throttler.scanner import start_scan

logger = logging.getLogger(__name__)

# Oracle calls running longer than this are logged every tick until they return.
_SLOW_CALL_SECONDS = 300


def restart_lapsed_jobs(job_store) -> None:
    """Restart jobs whose lease lapsed: a granule job resumes at its cursor, a concept
    type is republished from scratch (idempotent)."""
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
        try:
            if concept_type.startswith("granules"):
                start_scan(job_id)
            elif concept_type in reindex.ROUTE_TO_INTERNAL_TYPE:
                threading.Thread(
                    target=reindex.publish_concept_type,
                    args=(job_id, reindex.ROUTE_TO_INTERNAL_TYPE[concept_type], job.get("before")),
                    name=f"restart-{job_id}", daemon=True,
                ).start()
            else:
                # A single-concept request ran synchronously; there's nothing to resume.
                job_store.mark_job(job_id, "failed")
        except Exception as exc:
            logger.error({"event": "lapsed_job_restart_error", "job_id": job_id, "error": str(exc)})
            job_store.mark_job(job_id, "failed")


def _tick(job_store, stop_event: threading.Event) -> None:
    # Separate try blocks, so one failing step doesn't skip the others.
    for job_id in leases.held_jobs():
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
    """Tick now and then five times per lease. Ignores stop_event: work can outlive SIGTERM
    (a scan stuck in an Oracle call, a BackgroundTask uvicorn waits on) and must stay
    leased until it ends."""
    def _run() -> None:
        while True:
            _tick(job_store, stop_event)
            time.sleep(config.lease_minutes * 60 / 5)

    thread = threading.Thread(target=_run, name="lease-keeper", daemon=True)
    thread.start()
    logger.info({"event": "lease_keeper_started"})
    return thread
