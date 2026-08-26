import logging
from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query

from app.auth import require_auth
from app.config import config
from app.db.dynamo import job_store
from app.es.health import check_all_es_health
from app.sqs.client import get_queue_depth
from app.throttler.worker import throttler

router = APIRouter()
logger = logging.getLogger(__name__)

_TS_FMT = "%Y-%m-%dT%H:%M:%SZ"


def _enrich_job(job: dict) -> dict:
    now = datetime.now(timezone.utc)
    result = dict(job)

    if "started_at" in job:
        try:
            started = datetime.strptime(job["started_at"], _TS_FMT).replace(tzinfo=timezone.utc)
            result["elapsed_seconds"] = int((now - started).total_seconds())
        except Exception:
            pass

    if "last_heartbeat" in job:
        try:
            hb = datetime.strptime(job["last_heartbeat"], _TS_FMT).replace(tzinfo=timezone.utc)
            age = int((now - hb).total_seconds())
            result["heartbeat_age_seconds"] = age
            result["heartbeat_stale"] = age > config.stall_minutes * 60
        except Exception:
            pass

    dispatched = job.get("total_dispatched", 0)
    expected = job.get("total_granules_expected", 0)
    if expected > 0:
        result["pct_complete"] = round(dispatched / expected * 100, 1)

    return result


@router.get("/status")
async def status():
    es_health = check_all_es_health()

    try:
        page_queue_depth = get_queue_depth(config.intermediate_queue_url)
    except Exception as exc:
        logger.warning({"event": "queue_depth_check_failed", "queue": "intermediate", "error": str(exc)})
        page_queue_depth = -1

    try:
        collection_queue_depth = get_queue_depth(config.collection_queue_url)
    except Exception as exc:
        logger.warning({"event": "queue_depth_check_failed", "queue": "collection", "error": str(exc)})
        collection_queue_depth = -1

    try:
        indexer_queue_depth = get_queue_depth(config.indexer_queue_url)
    except Exception as exc:
        logger.warning({"event": "queue_depth_check_failed", "queue": "indexer", "error": str(exc)})
        indexer_queue_depth = -1

    liveness = throttler.liveness()
    tokens = throttler.token_state()

    return {
        "es_health": es_health,
        "page_queue_depth": page_queue_depth,
        "collection_queue_depth": collection_queue_depth,
        "indexer_queue_depth": indexer_queue_depth,
        "throttler_alive": liveness["alive"],
        "throttler_last_active": liveness["last_active"],
        "rate_per_minute": tokens["rate_per_minute"],
        "tokens_available": tokens["tokens_available"],
    }


@router.get("/jobs")
async def list_jobs(status: Optional[str] = Query(None), limit: int = Query(50, ge=1, le=200)):
    jobs = job_store.list_jobs(status_filter=status, limit=limit)
    return {"jobs": [_enrich_job(j) for j in jobs], "count": len(jobs)}


@router.get("/jobs/{job_id}")
async def get_job(job_id: str):
    job = job_store.get_job(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail=f"Job not found: {job_id}")
    return _enrich_job(job)


@router.delete("/jobs/{job_id}")
async def cancel_job(job_id: str, _token: str = Depends(require_auth)):
    job = job_store.get_job(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail=f"Job not found: {job_id}")
    if not job_store.try_cancel_job(job_id):
        raise HTTPException(status_code=409, detail=f"Job {job_id} is already terminal")
    logger.info({"event": "job_cancelled", "job_id": job_id})
    return {"job_id": job_id, "status": "cancelled"}
