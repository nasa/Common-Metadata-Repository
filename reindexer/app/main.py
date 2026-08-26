import json
import logging
import signal
import sys
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.config import config
from app.db import db_client
from app.db.dynamo import job_store
from app.routers import health, reindex, status
from app.routers import throttle as throttle_router
from app.startup import resume_stalled_jobs
from app.sqs.client import enqueue_collection_item
from app.throttler.cancel_cache import CancelledJobCache
from app.throttler.worker import throttler


class _JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        if isinstance(record.msg, dict):
            payload = {"level": record.levelname, "logger": record.name, **record.msg}
        else:
            payload = {"level": record.levelname, "logger": record.name, "message": record.getMessage()}
        return json.dumps(payload)


def _configure_logging() -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(_JsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(logging.INFO)


_configure_logging()
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    cancel_cache = CancelledJobCache(job_store)

    _orig_sigterm = signal.getsignal(signal.SIGTERM)

    def _on_sigterm(signum, frame):
        logger.info({"event": "sigterm_received"})
        cancel_cache.stop()
        throttler.stop()
        if callable(_orig_sigterm):
            _orig_sigterm(signum, frame)

    signal.signal(signal.SIGTERM, _on_sigterm)

    resume_stalled_jobs(db_client, job_store, enqueue_collection_item)

    cancel_cache.start()
    throttler.set_cancel_cache(cancel_cache)
    throttler.start()

    yield

    cancel_cache.stop()
    throttler.stop()


app = FastAPI(
    title=config.service_name,
    version=config.service_version,
    lifespan=lifespan,
)

app.include_router(health.router, prefix="/reindexer")
app.include_router(status.router, prefix="/reindexer")
app.include_router(reindex.router, prefix="/reindexer")
app.include_router(throttle_router.router, prefix="/reindexer")
