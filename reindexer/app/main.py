import json
import logging
import signal
import sys
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.config import config
from app.db.dynamo import job_store
from app.lease_keeper import start_lease_keeper
from app.routers import health, reindex, status
from app.routers import throttle as throttle_router
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

    logger.info({
        "event": "startup_config",
        "version": config.service_version,
        "db_backend": config.db_backend,
        "db_dsn": f"{config.db_host}:{config.db_port}/{config.db_service}",
        "db_user": config.db_user,
        "oracle_pool_min": config.oracle_pool_min,
        "oracle_pool_max": config.oracle_pool_max,
        "oracle_pool_increment": config.oracle_pool_increment,
        "aws_region": config.aws_region,
        "sqs_endpoint_url": config.sqs_endpoint_url,
        "dynamodb_endpoint_url": config.dynamodb_endpoint_url,
        "indexer_queue_url": config.indexer_queue_url,
        "dynamodb_job_table": config.dynamodb_table_name,
        "es_collections": f"{config.es_host}:{config.es_col_port}",
        "es_granules": f"{config.es_gran_host}:{config.es_gran_port}",
        "acl_base_url": config.acl_base_url,
        "rate_per_minute": config.rate_per_minute,
        "stream_chunk_size": config.stream_chunk_size,
        "id_range_chunk_size": config.id_range_chunk_size,
        "sqs_send_workers": config.sqs_send_workers,
        "lease_minutes": config.lease_minutes,
        "cancel_check_interval_seconds": config.cancel_check_interval_seconds,
    })

    # Before the lease keeper, so jobs it restarts can see cancellations.
    cancel_cache.start()
    throttler.set_cancel_cache(cancel_cache)

    start_lease_keeper(job_store, throttler.stop_event)

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
