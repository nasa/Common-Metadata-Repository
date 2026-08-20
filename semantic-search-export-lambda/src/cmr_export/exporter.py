import hashlib
import json
import logging
import random
import tempfile
import time
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from uuid import uuid4

from .transform import collection, variable

LOG = logging.getLogger(__name__)
COLLECTION_SOURCE = [
    "concept-id",
    "short-name",
    "entry-title",
    "summary",
    "provider-id",
    "science-keywords",
    "science-keywords-flat",
    "platform-sn",
    "instrument-sn",
    "temporals",
    "mbr-west",
    "mbr-south",
    "mbr-east",
    "mbr-north",
    "mbr-crosses-antimeridian",
    "variable-concept-ids",
]
VARIABLE_SOURCE = ["concept-id", "variable-name", "measurement", "definition"]
TRANSIENT = {429, 502, 503, 504}


def retry(call, attempts=4, sleeper=time.sleep, jitter=random.random):
    for attempt in range(attempts):
        try:
            return call()
        except Exception as error:
            status = getattr(error, "status_code", None) or getattr(error, "status", None)
            if status not in TRANSIENT or attempt == attempts - 1:
                raise
            sleeper((0.25 * (2**attempt)) + (0.1 * jitter()))


def fetch_collections(es, alias, providers, limit, page_size):
    pit = retry(lambda: es.open_point_in_time(index=alias, keep_alive="2m"))["id"]
    found = []
    after = None
    try:
        while len(found) < limit + 1:
            filters = [{"term": {"deleted": False}}]
            if providers:
                filters.append({"terms": {"provider-id": providers}})
            body = {
                "size": min(page_size, limit + 1 - len(found)),
                "pit": {"id": pit, "keep_alive": "2m"},
                "sort": [{"concept-id": "asc"}, {"_shard_doc": "asc"}],
                "query": {"bool": {"filter": filters}},
                "_source": COLLECTION_SOURCE,
            }
            if after is not None:
                body["search_after"] = after
            response = retry(lambda: es.search(**body))
            hits = response.get("hits", {}).get("hits", [])
            if not hits:
                break
            found.extend(hit["_source"] for hit in hits)
            after = hits[-1]["sort"]
        return found[:limit], len(found) > limit
    finally:
        retry(lambda: es.close_point_in_time(id=pit))


def fetch_variables(es, alias, ids, batch_size):
    result = {}
    ordered = sorted(set(ids))
    for offset in range(0, len(ordered), batch_size):
        batch = ordered[offset : offset + batch_size]
        response = retry(
            lambda b=batch: es.search(
                index=alias,
                size=len(b),
                query={
                    "bool": {"filter": [{"terms": {"concept-id": b}}, {"term": {"deleted": False}}]}
                },
                source=VARIABLE_SOURCE,
                sort=[{"concept-id": "asc"}],
            )
        )
        for hit in response.get("hits", {}).get("hits", []):
            value = variable(hit["_source"])
            result[value["concept_id"]] = value
    return result


def build_records(es, settings, providers, limit):
    sources, truncated = fetch_collections(
        es, settings.collection_alias, providers, limit, settings.page_size
    )
    sources.sort(key=lambda item: item.get("concept-id", ""))
    ids = [item for source in sources for item in source.get("variable-concept-ids", [])]
    variables = fetch_variables(es, settings.variable_alias, ids, settings.variable_batch_size)
    seen_variables, warnings, records = set(), Counter(), []
    seen_collections = set()
    for source in sources:
        concept_id = source.get("concept-id")
        if concept_id in seen_collections:
            raise ValueError(f"duplicate collection concept ID: {concept_id}")
        seen_collections.add(concept_id)
        joined = []
        for variable_id in sorted(set(source.get("variable-concept-ids", []))):
            if variable_id in seen_variables:
                warnings["duplicate_variable_association"] += 1
            elif variable_id in variables:
                joined.append(variables[variable_id])
                seen_variables.add(variable_id)
            else:
                warnings["missing_variable"] += 1
        record, warning = collection(source, joined)
        if warning:
            warnings[f"spatial_{warning}"] += 1
        records.append(record)
    return records, truncated, warnings


def write_jsonl(records, path: Path):
    digest = hashlib.sha256()
    size = 0
    variables = 0
    with path.open("wb") as destination:
        for record in records:
            encoded = (
                json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
            ).encode()
            destination.write(encoded)
            digest.update(encoded)
            size += len(encoded)
            variables += len(record.get("variables", []))
    return size, digest.hexdigest(), variables


def upload(s3, settings, bucket, key, path, metadata):
    staging = f"{key}.staging-{uuid4().hex}"
    extra = {"ContentType": "application/x-ndjson", "Metadata": metadata}
    if settings.sse:
        extra["ServerSideEncryption"] = settings.sse
    if settings.kms_key_id:
        extra["SSEKMSKeyId"] = settings.kms_key_id
    try:
        s3.upload_file(str(path), bucket, staging, ExtraArgs=extra)
        copy_args = {k: v for k, v in extra.items() if k != "ContentType"}
        copy_args.update(
            {
                "CopySource": {"Bucket": bucket, "Key": staging},
                "Bucket": bucket,
                "Key": key,
                "MetadataDirective": "REPLACE",
                "ContentType": "application/x-ndjson",
            }
        )
        s3.copy_object(**copy_args)
    finally:
        s3.delete_object(Bucket=bucket, Key=staging)


def run_export(es, s3, settings, bucket, key, providers, limit):
    records, truncated, warnings = build_records(es, settings, providers, limit)
    if not records:
        raise ValueError("selection produced no collections")
    with tempfile.TemporaryDirectory(dir="/tmp") as directory:
        path = Path(directory) / "collections.jsonl"
        size, sha256, variable_count = write_jsonl(records, path)
        metadata = {
            "schema-version": "1",
            "collection-count": str(len(records)),
            "variable-count": str(variable_count),
            "collection-alias": settings.collection_alias,
            "variable-alias": settings.variable_alias,
            "exported-at": datetime.now(UTC).isoformat(),
        }
        upload(s3, settings, bucket, key, path, metadata)
    LOG.info(
        json.dumps(
            {
                "event": "export_complete",
                "collections": len(records),
                "variables": variable_count,
                "truncated": truncated,
                "warnings": dict(warnings),
                "bucket": bucket,
                "key": key,
            }
        )
    )
    return {
        "bucket": bucket,
        "key": key,
        "schema_version": 1,
        "collections": len(records),
        "variables": variable_count,
        "bytes": size,
        "sha256": sha256,
        "max_collections": limit,
        "more_matching": truncated,
        "warnings": dict(warnings),
    }
