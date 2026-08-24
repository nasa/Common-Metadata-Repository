import json
import logging
from datetime import UTC, datetime
from uuid import uuid4

from .config import Settings, invocation
from .exporter import run_export
from .handler import clients

LOG = logging.getLogger(__name__)
LOG.setLevel(logging.INFO)


def versioned_key(prefix: str, now=None, identifier=None) -> str:
    timestamp = (now or datetime.now(UTC)).strftime("%Y%m%dT%H%M%SZ")
    suffix = identifier or uuid4().hex
    name = f"collections-{timestamp}-{suffix}.jsonl"
    prefix = prefix.strip("/")
    return f"{prefix}/{name}" if prefix else name


def task_event(settings: Settings) -> dict:
    key = settings.key or versioned_key(settings.s3_prefix)
    return {"bucket": settings.bucket, "key": key}


def main() -> None:
    try:
        settings = Settings.from_env()
        bucket, key, providers, limit = invocation(task_event(settings), settings)
        es, s3 = clients(settings)
        result = run_export(es, s3, settings, bucket, key, providers, limit)
        LOG.info(json.dumps({"event": "task_complete", "result": result}))
    except Exception as error:
        LOG.exception(json.dumps({"event": "task_failed", "category": type(error).__name__}))
        raise


if __name__ == "__main__":
    main()
