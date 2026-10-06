import logging
import socket
from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query

from app import leases
from app.auth import require_auth
from app.config import config
from app.db.dynamo import job_store
from app.es.health import check_all_es_health
from app.lease_keeper import jobs_in_progress
from app.sqs.client import get_queue_counts
from app.throttler.worker import throttler

router = APIRouter()
logger = logging.getLogger(__name__)

_TS_FMT = "%Y-%m-%dT%H:%M:%SZ"


def _parse_ts(value: str) -> datetime:
    return datetime.strptime(value, _TS_FMT).replace(tzinfo=timezone.utc)


def _enrich_job(job: dict) -> dict:
    now = datetime.now(timezone.utc)
    result = dict(job)
    result.pop("ttl", None)

    if "started_at" in job:
        try:
            # A stopped job is measured up to completed_at, so elapsed (and the rate
            # below) don't keep changing afterwards.
            end = _parse_ts(job["completed_at"]) if job.get("completed_at") else now
            result["elapsed_seconds"] = int((end - _parse_ts(job["started_at"])).total_seconds())
        except Exception:
            pass

    if "last_heartbeat" in job:
        try:
            hb = _parse_ts(job["last_heartbeat"])
            age = int((now - hb).total_seconds())
            result["heartbeat_age_seconds"] = age
            if leases.is_heartbeat_leased(job):
                result["lease_lapsed"] = age > config.lease_minutes * 60
        except Exception:
            pass

    dispatched = job.get("total_dispatched", 0)
    elapsed = result.get("elapsed_seconds", 0)
    if dispatched > 0 and elapsed > 0:
        result["avg_dispatch_rate_per_minute"] = round(dispatched / elapsed * 60)

    # Providers never enqueued, or with collections not yet streamed: the set to
    # resubmit after a cancel.
    if "providers_requested" in job:
        to_process = set(job.get("providers_requested") or [])
        enqueued = set(job.get("providers_enqueued") or [])
        work_items = job.get("providers_work_items") or {}
        split = job.get("providers_collections_split") or {}
        never_enqueued = to_process - enqueued
        still_splitting = {p for p in enqueued if split.get(p, 0) < work_items.get(p, 0)}
        result["providers_remaining"] = sorted(never_enqueued | still_splitting)

    return result


def _queue_counts(name: str, url: str) -> Optional[dict]:
    try:
        return get_queue_counts(url)
    except Exception as exc:
        logger.warning({"event": "queue_counts_check_failed", "queue": name, "error": str(exc)})
        return None


# The handlers below are plain def, so FastAPI runs their blocking SQS/DynamoDB calls
# in its threadpool instead of on the event loop that also serves /health.

@router.get("/status")
def status():
    """Shared dependencies, plus what the answering task is doing. Fields under
    "task" are per task: during a deploy, calls can reach different tasks."""
    return {
        "task": {
            "id": socket.gethostname(),
            "jobs_in_progress": sorted(jobs_in_progress()),
            "collection_worker": {"alive": throttler.is_alive(), "current_job": throttler.current_job_id},
            "rate_limit_per_minute": throttler.get_rate(),
        },
        "es_health": check_all_es_health(),
        "queues": {
            "collection": _queue_counts("collection", config.collection_queue_url),
            "indexer": _queue_counts("indexer", config.indexer_queue_url),
        },
    }


@router.get("/jobs")
def list_jobs(status: Optional[str] = Query(None), limit: int = Query(50, ge=1, le=200)):
    jobs = job_store.list_jobs(status_filter=status, limit=limit)
    return {"jobs": [_enrich_job(j) for j in jobs], "count": len(jobs)}


@router.get("/jobs/{job_id}")
def get_job(job_id: str):
    job = job_store.get_job(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail=f"Job not found: {job_id}")
    return _enrich_job(job)


@router.delete("/jobs/{job_id}")
def cancel_job(job_id: str, _token: str = Depends(require_auth)):
    job = job_store.get_job(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail=f"Job not found: {job_id}")
    if not job_store.try_cancel_job(job_id):
        raise HTTPException(status_code=409, detail=f"Job {job_id} is already terminal")
    logger.info({"event": "job_cancelled", "job_id": job_id})
    return {"job_id": job_id, "status": "cancelled"}
