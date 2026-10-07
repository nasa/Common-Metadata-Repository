"""/reindex/* endpoints. Granule jobs run as scans (app.throttler.scanner), concept-type
jobs as background tasks.

Handlers are plain def, so their blocking DB/SQS calls run in FastAPI's threadpool
rather than on the event loop that serves /health.
"""
import logging
import re
import uuid
from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import APIRouter, BackgroundTasks, Depends, Header, HTTPException, Request
from pydantic import BaseModel

from app import leases
from app.auth import require_auth
from app.db import db_client
from app.db.dynamo import job_store
from app.es.health import check_all_es_health
from app.sqs.client import publish_concept_update, publish_indexer_events_batch
from app.throttler.scanner import start_scan
from app.throttler.worker import throttler

router = APIRouter()
logger = logging.getLogger(__name__)

# URL path segment → db_client concept type
ROUTE_TO_INTERNAL_TYPE: dict[str, str] = {
    "variables":             "variable",
    "services":              "service",
    "tools":                 "tool",
    "collections":           "collection",
    "generics":              "generic",
    "data-quality-summaries": "data-quality-summary",
    "order-options":         "order-option",
    "visualizations":        "visualization",
    "subscriptions":         "subscription",
    "grids":                 "grid",
    "citations":             "citation",
}

_CONCEPT_ID_RE = re.compile(r'^[A-Z]+\d+-[A-Z0-9_]+\Z')
_PROVIDER_ID_RE = re.compile(r'^[A-Z0-9_]+\Z')

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


def _provider_of(concept_id: str) -> str:
    return concept_id.split("-", 1)[1]


def _reject_small_providers(provider_ids: list[str]) -> None:
    """Disabled until paging their shared SMALL_PROV_GRANULES table is fixed."""
    small = [p for p in provider_ids if db_client.is_small_provider(p)]
    if small:
        raise HTTPException(status_code=400, detail=f"Granule reindexing is disabled for small providers: {small!r}")


def _known_providers() -> list[str]:
    try:
        return db_client.get_all_provider_ids()
    except Exception as exc:
        logger.error({"event": "provider_existence_check_failed", "error": str(exc)})
        raise HTTPException(status_code=503, detail="Unable to validate provider IDs; database unavailable") from exc


def _validate_providers(provider_ids: list[str]) -> None:
    known_providers = _known_providers()
    unknown = [p for p in provider_ids if p not in known_providers]
    if unknown:
        raise HTTPException(status_code=400, detail=f"Unknown provider ID(s): {unknown!r}")
    _reject_small_providers(provider_ids)


class ProviderListRequest(BaseModel):
    provider_ids: list[str]


def _source_url(request: Request) -> str:
    return f"{request.url.path}?{request.url.query}" if request.url.query else request.url.path


def _start_granule_job(
    request: Request, concept_type: str, after: Optional[str], before: Optional[str],
    *, providers: Optional[list[str]] = None, collection_id: Optional[str] = None,
) -> str:
    """before defaults to now, so a run leaves granules ingested meanwhile to normal indexing."""
    before = before or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    request_id = str(uuid.uuid4())
    job_store.create_job(
        request_id, concept_type, providers=providers, collection_id=collection_id,
        after=after, before=before, source_url=_source_url(request),
    )
    logger.info({
        "event": "reindex_granules_requested",
        "request_id": request_id,
        "concept_type": concept_type,
        "providers": providers,
        "collection_id": collection_id,
        "after": after,
        "before": before,
    })
    start_scan(request_id)
    return request_id


# Concepts per SQS send and per total_dispatched update.
_CONCEPT_TYPE_BATCH_SIZE = 500


@leases.holding
def publish_concept_type(request_id: str, internal_type: str, before: Optional[str] = None) -> None:
    """Not rate limited; fails the job if ES isn't green at the start. Also called by the
    lease keeper to restart a job whose owner died."""
    try:
        if check_all_es_health()["overall"] != "green":
            logger.warning({
                "event": "concept_type_reindex_es_not_green",
                "request_id": request_id,
                "concept_type": internal_type,
            })
            job_store.mark_job(request_id, "failed")
            return

        dispatched = 0
        batch: list[tuple[str, int]] = []

        for concept_id, revision_id in db_client.stream_concept_ids_by_type(internal_type, before=before):
            batch.append((concept_id, revision_id))
            if len(batch) >= _CONCEPT_TYPE_BATCH_SIZE:
                if throttler.is_job_cancelled(request_id):
                    logger.info({"event": "concept_type_reindex_cancelled", "request_id": request_id})
                    return
                publish_indexer_events_batch(batch, request_id)
                dispatched += len(batch)
                job_store.update_dispatched(request_id, len(batch))
                batch = []

        if batch:
            if throttler.is_job_cancelled(request_id):
                logger.info({"event": "concept_type_reindex_cancelled", "request_id": request_id})
                return
            publish_indexer_events_batch(batch, request_id)
            dispatched += len(batch)
            job_store.update_dispatched(request_id, len(batch))

        if throttler.is_job_cancelled(request_id):
            logger.info({"event": "concept_type_reindex_cancelled", "request_id": request_id})
            return
        job_store.mark_job(request_id, "completed")
        logger.info({
            "event": "concept_type_reindex_complete",
            "request_id": request_id,
            "concept_type": internal_type,
            "count": dispatched,
        })
    except Exception as exc:
        logger.error({
            "event": "concept_type_reindex_error",
            "request_id": request_id,
            "concept_type": internal_type,
            "error": str(exc),
        })
        job_store.mark_job(request_id, "failed")


@router.post("/reindex/granules", status_code=202)
def reindex_granules(
    request: Request,
    after: Optional[str] = None,
    before: Optional[str] = None,
    override: bool = Depends(_override_flag),
    _token: str = Depends(require_auth),
):
    _validate_date_params(after, before, override)
    providers = _known_providers()
    small = [p for p in providers if db_client.is_small_provider(p)]
    if small:
        logger.warning({"event": "small_providers_skipped", "provider_ids": small})
    request_id = _start_granule_job(request, "granules", after, before, providers=[p for p in providers if p not in small])
    return {"request_id": request_id, "message": "Reindex started for all providers"}


@router.post("/reindex/granules/provider/{provider_id}", status_code=202)
def reindex_granules_by_provider(
    provider_id: str,
    request: Request,
    after: Optional[str] = None,
    before: Optional[str] = None,
    override: bool = Depends(_override_flag),
    _token: str = Depends(require_auth),
):
    if not _PROVIDER_ID_RE.match(provider_id):
        raise HTTPException(status_code=400, detail=f"Invalid provider ID format: {provider_id!r}")
    _validate_date_params(after, before, override)
    _validate_providers([provider_id])
    request_id = _start_granule_job(request, "granules-by-provider", after, before, providers=[provider_id])
    return {"request_id": request_id, "message": f"Reindex started for provider {provider_id}"}


@router.post("/reindex/granules/providers", status_code=202)
def reindex_granules_by_providers(
    body: ProviderListRequest,
    request: Request,
    after: Optional[str] = None,
    before: Optional[str] = None,
    override: bool = Depends(_override_flag),
    _token: str = Depends(require_auth),
):
    if not body.provider_ids:
        raise HTTPException(status_code=400, detail="provider_ids must not be empty")
    invalid = [p for p in body.provider_ids if not _PROVIDER_ID_RE.match(p)]
    if invalid:
        raise HTTPException(status_code=400, detail=f"Invalid provider ID format: {invalid!r}")
    _validate_date_params(after, before, override)
    _validate_providers(body.provider_ids)
    providers = list(dict.fromkeys(body.provider_ids))  # de-dupe, preserve order
    request_id = _start_granule_job(request, "granules-by-providers", after, before, providers=providers)
    return {"request_id": request_id, "message": f"Reindex started for {len(providers)} providers"}


@router.post("/reindex/granules/collection/{collection_id:path}", status_code=202)
def reindex_granules_by_collection(
    collection_id: str,
    request: Request,
    after: Optional[str] = None,
    before: Optional[str] = None,
    override: bool = Depends(_override_flag),
    _token: str = Depends(require_auth),
):
    if not _CONCEPT_ID_RE.match(collection_id):
        raise HTTPException(status_code=400, detail=f"Invalid collection ID format: {collection_id!r}")
    _validate_date_params(after, before, override)
    _reject_small_providers([_provider_of(collection_id)])
    request_id = _start_granule_job(request, "granules-by-collection", after, before, collection_id=collection_id)
    return {"request_id": request_id, "message": f"Reindex started for collection {collection_id}"}


@router.post("/reindex/concept/{concept_id}", status_code=202)
def reindex_concept(
    concept_id: str,
    request: Request,
    _token: str = Depends(require_auth),
):
    if not _CONCEPT_ID_RE.match(concept_id):
        raise HTTPException(status_code=400, detail=f"Invalid CMR concept ID format: {concept_id!r}")
    if re.match(r"G\d", concept_id):  # granule; not GRD (grids)
        _reject_small_providers([_provider_of(concept_id)])

    request_id = str(uuid.uuid4())
    job_store.create_job(request_id, "concept", concept_id=concept_id, source_url=_source_url(request))
    logger.info({
        "event": "reindex_concept_requested",
        "request_id": request_id,
        "concept_id": concept_id,
    })

    with leases.held(request_id):
        concept = db_client.get_concept_by_id(concept_id)
        if concept is None:
            job_store.mark_job(request_id, "failed")
            raise HTTPException(status_code=404, detail=f"Concept not found: {concept_id}")

        es_health = check_all_es_health()
        if es_health["overall"] != "green":
            logger.warning({
                "event": "reindex_concept_es_not_green",
                "request_id": request_id,
                "concept_id": concept_id,
            })
            job_store.mark_job(request_id, "failed")
            raise HTTPException(status_code=503, detail="Elasticsearch cluster is not green; retry later")

        try:
            publish_concept_update(concept["concept-id"], concept["revision-id"], request_id)
            job_store.update_dispatched(request_id, 1)
            job_store.mark_job(request_id, "completed")
        except Exception as exc:
            logger.error({"event": "concept_publish_error", "request_id": request_id, "error": str(exc)})
            job_store.mark_job(request_id, "failed")
            raise

    return {"request_id": request_id, "message": f"Reindex queued for concept {concept_id}"}


# Registered last, so it doesn't capture /reindex/granules.
@router.post("/reindex/{concept_type}", status_code=202)
def reindex_by_concept_type(
    concept_type: str,
    request: Request,
    background_tasks: BackgroundTasks,
    _token: str = Depends(require_auth),
):
    internal_type = ROUTE_TO_INTERNAL_TYPE.get(concept_type)
    if internal_type is None:
        raise HTTPException(status_code=404, detail=f"Unknown concept type: {concept_type!r}")

    before = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    request_id = str(uuid.uuid4())
    job_store.create_job(request_id, concept_type, before=before, source_url=_source_url(request))
    logger.info({
        "event": "reindex_concept_type_requested",
        "request_id": request_id,
        "concept_type": concept_type,
        "internal_type": internal_type,
    })
    background_tasks.add_task(publish_concept_type, request_id, internal_type, before)
    return {"request_id": request_id, "message": f"Reindex started for concept type {concept_type}"}
