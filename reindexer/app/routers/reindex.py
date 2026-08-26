import logging
import re
import uuid
from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import APIRouter, BackgroundTasks, Depends, Header, HTTPException

from app.auth import require_auth
from app.db import db_client
from app.db.dynamo import job_store
from app.sqs.client import enqueue_collection_item, publish_concept_update
from app.throttler.worker import throttler

router = APIRouter()
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Concept type routing
# ---------------------------------------------------------------------------

# URL path segment → internal concept type used by db_client
_ROUTE_TO_INTERNAL_TYPE: dict[str, str] = {
    "variables":             "variable",
    "services":              "service",
    "tools":                 "tool",
    "collections":           "collection",
    "generics":              "generic",
    "data-quality-summaries": "data-quality-summary",
    "order-options":         "order-option",
    "visualizations":        "visualization",
    "subscriptions":         "subscription",
    "grid":                  "grid",
    "citation":              "citation",
}

# CMR concept-id format: one or more uppercase letters, digits, hyphen, provider (uppercase letters/digits/underscores)
_CONCEPT_ID_RE = re.compile(r'^[A-Z]+\d+-[A-Z0-9_]+$')
_PROVIDER_ID_RE = re.compile(r'^[A-Z0-9_]+$')

# ---------------------------------------------------------------------------
# Date validation helpers
# ---------------------------------------------------------------------------

_ISO8601_Z_RE = re.compile(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$')


def _parse_utc_z(value: str, param_name: str) -> datetime:
    if not _ISO8601_Z_RE.match(value):
        raise HTTPException(
            status_code=400,
            detail=f"{param_name} must be ISO8601 UTC with Z suffix (e.g. 2024-01-01T00:00:00Z)",
        )
    return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)


def _validate_date_params(
    after: Optional[str],
    before: Optional[str],
    override_date_limit: bool,
) -> None:
    after_dt: Optional[datetime] = None
    before_dt: Optional[datetime] = None

    if after:
        after_dt = _parse_utc_z(after, "after")
    if before:
        before_dt = _parse_utc_z(before, "before")

    if after_dt and before_dt and after_dt >= before_dt:
        raise HTTPException(status_code=400, detail="after must be before the before parameter")

    if after_dt and not override_date_limit:
        cutoff = datetime.now(timezone.utc) - timedelta(days=30)
        if after_dt < cutoff:
            raise HTTPException(
                status_code=400,
                detail="after is more than 30 days in the past; set X-CMR-Override-Date-Limit: true to bypass",
            )


def _override_flag(x_cmr_override_date_limit: Optional[str] = Header(None)) -> bool:
    return (x_cmr_override_date_limit or "").lower() == "true"


# ---------------------------------------------------------------------------
# Background helpers
# ---------------------------------------------------------------------------

def _enqueue_all_providers(request_id: str, after: Optional[str], before: Optional[str]) -> None:
    try:
        provider_ids = db_client.get_all_provider_ids()
        job_store.update_progress(request_id, providers_to_process=provider_ids)
        for provider_id in provider_ids:
            if throttler.is_job_cancelled(request_id):
                logger.info({"event": "enqueue_cancelled", "request_id": request_id, "provider_id": provider_id})
                return
            collection_ids = db_client.get_collection_ids_for_provider(provider_id)
            for cid in collection_ids:
                enqueue_collection_item(
                    request_id=request_id, collection_id=cid, after=after, before=before
                )
            job_store.update_progress(
                request_id,
                provider_enqueued=provider_id,
                work_items_delta=len(collection_ids),
            )
        job_store.mark_job(request_id, "dispatching")
        job_store.try_complete_job(request_id)
        logger.info({
            "event": "all_granules_enqueued",
            "request_id": request_id,
            "provider_count": len(provider_ids),
        })
    except Exception as exc:
        logger.error({"event": "all_granules_enqueue_error", "request_id": request_id, "error": str(exc)})
        job_store.mark_job(request_id, "failed")


def _enqueue_provider(
    request_id: str, provider_id: str, after: Optional[str], before: Optional[str]
) -> None:
    try:
        if throttler.is_job_cancelled(request_id):
            logger.info({"event": "enqueue_cancelled", "request_id": request_id, "provider_id": provider_id})
            return
        collection_ids = db_client.get_collection_ids_for_provider(provider_id)
        for cid in collection_ids:
            enqueue_collection_item(
                request_id=request_id, collection_id=cid, after=after, before=before
            )
        job_store.update_progress(
            request_id,
            provider_enqueued=provider_id,
            work_items_delta=len(collection_ids),
        )
        job_store.mark_job(request_id, "dispatching")
        job_store.try_complete_job(request_id)
        logger.info({
            "event": "provider_granules_enqueued",
            "request_id": request_id,
            "provider_id": provider_id,
            "collection_count": len(collection_ids),
        })
    except Exception as exc:
        logger.error({
            "event": "provider_granules_enqueue_error",
            "request_id": request_id,
            "provider_id": provider_id,
            "error": str(exc),
        })
        job_store.mark_job(request_id, "failed")


def _publish_concept_type(request_id: str, internal_type: str, before: Optional[str] = None) -> None:
    try:
        concept_ids = db_client.get_concept_ids_by_type(internal_type, before=before)
        for concept_id, revision_id in concept_ids:
            if throttler.is_job_cancelled(request_id):
                logger.info({"event": "concept_type_reindex_cancelled", "request_id": request_id})
                return
            publish_concept_update(concept_id, revision_id, request_id)
        job_store.update_dispatched(request_id, len(concept_ids))
        if throttler.is_job_cancelled(request_id):
            logger.info({"event": "concept_type_reindex_cancelled", "request_id": request_id})
            return
        job_store.mark_job(request_id, "completed")
        logger.info({
            "event": "concept_type_reindex_complete",
            "request_id": request_id,
            "concept_type": internal_type,
            "count": len(concept_ids),
        })
    except Exception as exc:
        logger.error({
            "event": "concept_type_reindex_error",
            "request_id": request_id,
            "concept_type": internal_type,
            "error": str(exc),
        })
        job_store.mark_job(request_id, "failed")


# ---------------------------------------------------------------------------
# Granule endpoints (must be registered before the wildcard {concept_type})
# ---------------------------------------------------------------------------

@router.post("/reindex/granules", status_code=202)
async def reindex_granules(
    background_tasks: BackgroundTasks,
    after: Optional[str] = None,
    before: Optional[str] = None,
    override: bool = Depends(_override_flag),
    _token: str = Depends(require_auth),
):
    _validate_date_params(after, before, override)
    before = before or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    request_id = str(uuid.uuid4())
    job_store.create_job(request_id, "granules", after=after, before=before)
    logger.info({"event": "reindex_granules_requested", "request_id": request_id, "after": after, "before": before})
    background_tasks.add_task(_enqueue_all_providers, request_id, after, before)
    return {"request_id": request_id, "message": "Reindex started for all providers"}


@router.post("/reindex/granules/provider/{provider_id}", status_code=202)
async def reindex_granules_by_provider(
    provider_id: str,
    background_tasks: BackgroundTasks,
    after: Optional[str] = None,
    before: Optional[str] = None,
    override: bool = Depends(_override_flag),
    _token: str = Depends(require_auth),
):
    if not _PROVIDER_ID_RE.match(provider_id):
        raise HTTPException(status_code=400, detail=f"Invalid provider ID format: {provider_id!r}")
    _validate_date_params(after, before, override)
    before = before or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    request_id = str(uuid.uuid4())
    job_store.create_job(request_id, "granules-by-provider", provider_id=provider_id, after=after, before=before)
    logger.info({
        "event": "reindex_provider_requested",
        "request_id": request_id,
        "provider_id": provider_id,
        "after": after,
        "before": before,
    })
    background_tasks.add_task(_enqueue_provider, request_id, provider_id, after, before)
    return {"request_id": request_id, "message": f"Reindex started for provider {provider_id}"}


@router.post("/reindex/granules/collection/{collection_id:path}", status_code=202)
async def reindex_granules_by_collection(
    collection_id: str,
    after: Optional[str] = None,
    before: Optional[str] = None,
    override: bool = Depends(_override_flag),
    _token: str = Depends(require_auth),
):
    if not _CONCEPT_ID_RE.match(collection_id):
        raise HTTPException(status_code=400, detail=f"Invalid collection ID format: {collection_id!r}")
    _validate_date_params(after, before, override)
    before = before or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    request_id = str(uuid.uuid4())
    job_store.create_job(request_id, "granules-by-collection", collection_id=collection_id, after=after, before=before)
    logger.info({
        "event": "reindex_collection_requested",
        "request_id": request_id,
        "collection_id": collection_id,
        "after": after,
        "before": before,
    })
    try:
        enqueue_collection_item(
            request_id=request_id, collection_id=collection_id, after=after, before=before
        )
        job_store.update_progress(request_id, work_items_delta=1)
        job_store.mark_job(request_id, "dispatching")
    except Exception as exc:
        logger.error({"event": "collection_enqueue_error", "request_id": request_id, "error": str(exc)})
        job_store.mark_job(request_id, "failed")
        raise
    return {"request_id": request_id, "message": f"Reindex started for collection {collection_id}"}


# ---------------------------------------------------------------------------
# Single-concept endpoint (registered before wildcard {concept_type})
# ---------------------------------------------------------------------------

@router.post("/reindex/concept/{concept_id}", status_code=202)
async def reindex_concept(
    concept_id: str,
    _token: str = Depends(require_auth),
):
    if not _CONCEPT_ID_RE.match(concept_id):
        raise HTTPException(status_code=400, detail=f"Invalid CMR concept ID format: {concept_id!r}")

    request_id = str(uuid.uuid4())
    job_store.create_job(request_id, "concept", collection_id=concept_id)
    logger.info({
        "event": "reindex_concept_requested",
        "request_id": request_id,
        "concept_id": concept_id,
    })

    concept = db_client.get_concept_by_id(concept_id)
    if concept is None:
        job_store.mark_job(request_id, "failed")
        raise HTTPException(status_code=404, detail=f"Concept not found: {concept_id}")

    try:
        publish_concept_update(concept["concept-id"], concept["revision-id"], request_id)
        job_store.update_dispatched(request_id, 1)
        job_store.mark_job(request_id, "completed")
    except Exception as exc:
        logger.error({"event": "concept_publish_error", "request_id": request_id, "error": str(exc)})
        job_store.mark_job(request_id, "failed")
        raise

    return {"request_id": request_id, "message": f"Reindex queued for concept {concept_id}"}


# ---------------------------------------------------------------------------
# Non-granule concept type endpoint (wildcard — must come last)
# ---------------------------------------------------------------------------

@router.post("/reindex/{concept_type}", status_code=202)
async def reindex_by_concept_type(
    concept_type: str,
    background_tasks: BackgroundTasks,
    _token: str = Depends(require_auth),
):
    internal_type = _ROUTE_TO_INTERNAL_TYPE.get(concept_type)
    if internal_type is None:
        raise HTTPException(status_code=404, detail=f"Unknown concept type: {concept_type!r}")

    before = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    request_id = str(uuid.uuid4())
    job_store.create_job(request_id, concept_type, before=before)
    logger.info({
        "event": "reindex_concept_type_requested",
        "request_id": request_id,
        "concept_type": concept_type,
        "internal_type": internal_type,
    })
    background_tasks.add_task(_publish_concept_type, request_id, internal_type, before)
    return {"request_id": request_id, "message": f"Reindex started for concept type {concept_type}"}
