"""Resume stalled reindex jobs on service startup."""
import logging

from app.config import config

logger = logging.getLogger(__name__)


def resume_stalled_jobs(db_client, job_store, enqueue_fn) -> None:
    """Find jobs stalled mid-run and re-enqueue their remaining work."""
    stalled = job_store.find_stalled_jobs(stale_minutes=config.stall_minutes)
    if not stalled:
        logger.info({"event": "no_stalled_jobs"})
        return

    logger.info({"event": "stalled_jobs_found", "count": len(stalled)})

    for job in stalled:
        job_id = job["job_id"]
        last_heartbeat = job["last_heartbeat"]

        if not job_store.claim_stalled_job(job_id, last_heartbeat):
            logger.info({"event": "stalled_job_claim_lost", "job_id": job_id})
            continue

        concept_type = job.get("concept_type", "")
        logger.info({"event": "resuming_stalled_job", "job_id": job_id, "concept_type": concept_type})

        try:
            if concept_type == "granules":
                providers_to_process = set(job.get("providers_to_process") or [])
                providers_enqueued = set(job.get("providers_enqueued") or [])
                remaining = providers_to_process - providers_enqueued
                for provider_id in remaining:
                    for cid in db_client.get_collection_ids_for_provider(provider_id):
                        enqueue_fn(
                            request_id=job_id,
                            collection_id=cid,
                            after=job.get("after"),
                            before=job.get("before"),
                        )
                job_store.mark_job(job_id, "running")

            elif concept_type == "granules-by-provider":
                provider_id = job.get("provider_id")
                providers_enqueued = set(job.get("providers_enqueued") or [])
                if provider_id and provider_id not in providers_enqueued:
                    for cid in db_client.get_collection_ids_for_provider(provider_id):
                        enqueue_fn(
                            request_id=job_id,
                            collection_id=cid,
                            after=job.get("after"),
                            before=job.get("before"),
                        )
                job_store.mark_job(job_id, "running")

            elif concept_type == "granules-by-collection":
                collection_id = job.get("collection_id")
                if collection_id:
                    enqueue_fn(
                        request_id=job_id,
                        collection_id=collection_id,
                        after=job.get("after"),
                        before=job.get("before"),
                    )
                job_store.mark_job(job_id, "running")

            else:
                # Non-granule concept type jobs (variables, services, etc.) can't be resumed
                job_store.mark_job(job_id, "failed")

        except Exception as exc:
            logger.error({"event": "stalled_job_resume_error", "job_id": job_id, "error": str(exc)})
            job_store.mark_job(job_id, "failed")
