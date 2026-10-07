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
            # A stopped job is measured to completed_at, so elapsed and rate stop changing.
            end = _parse_ts(job["completed_at"]) if job.get("completed_at") else now
            result["elapsed_seconds"] = int((end - _parse_ts(job["started_at"])).total_seconds())
        except Exception:
            pass

    if "last_heartbeat" in job:
        try:
            hb = _parse_ts(job["last_heartbeat"])
            age = int((now - hb).total_seconds())
            result["heartbeat_age_seconds"] = age
            if job.get("status") == "running":
                result["lease_lapsed"] = age > config.lease_minutes * 60
        except Exception:
            pass

    dispatched = job.get("total_dispatched", 0)
    elapsed = result.get("elapsed_seconds", 0)
    if dispatched > 0 and elapsed > 0:
        result["avg_dispatch_rate_per_minute"] = round(dispatched / elapsed * 60)

    if "providers_requested" in job:
        done = set(job.get("providers_done") or [])
        result["providers_remaining"] = [p for p in job["providers_requested"] if p not in done]

    return result


def _indexer_queue_counts() -> Optional[dict]:
    try:
        return get_queue_counts(config.indexer_queue_url)
    except Exception as exc:
        logger.warning({"event": "queue_counts_check_failed", "error": str(exc)})
        return None


# Plain def, so their blocking calls run in FastAPI's threadpool, off the event loop
# that serves /health.

@router.get("/status")
def status():
    """Fields under "task" describe only the task that answered."""
    return {
        "task": {
            "id": socket.gethostname(),
            "jobs_in_progress": sorted(leases.held_jobs()),
            "rate_limit_per_minute": throttler.get_rate(),
        },
        "es_health": check_all_es_health(),
        "indexer_queue": _indexer_queue_counts(),
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
    return {"job_id": job_id, "status": "cancelled"}
