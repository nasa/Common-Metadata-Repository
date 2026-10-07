import os
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class Config:
    # Oracle DB
    db_host: str = field(default_factory=lambda: os.environ.get("DB_HOST", "localhost"))
    db_port: int = field(default_factory=lambda: int(os.environ.get("DB_PORT", "1521")))
    db_service: str = field(default_factory=lambda: os.environ.get("DB_SERVICE", "cmr"))
    db_user: str = field(default_factory=lambda: os.environ.get("DB_USER", "cmr"))
    db_password: str = field(default_factory=lambda: os.environ.get("DB_PASSWORD", ""))
    # Pool is shared by API requests and background jobs.
    oracle_pool_min: int = field(default_factory=lambda: int(os.environ.get("ORACLE_POOL_MIN", "2")))
    oracle_pool_max: int = field(default_factory=lambda: int(os.environ.get("ORACLE_POOL_MAX", "15")))
    oracle_pool_increment: int = field(default_factory=lambda: int(os.environ.get("ORACLE_POOL_INCREMENT", "1")))

    # Elasticsearch: collections and granules clusters
    es_host: str = field(default_factory=lambda: os.environ.get("CMR_ELASTIC_HOST", "localhost"))
    es_col_port: int = field(default_factory=lambda: int(os.environ.get("CMR_ELASTIC_PORT", "9211")))
    es_gran_host: str = field(default_factory=lambda: os.environ.get("CMR_GRAN_ELASTIC_HOST", "localhost"))
    es_gran_port: int = field(default_factory=lambda: int(os.environ.get("CMR_GRAN_ELASTIC_PORT", "9210")))

    # AWS
    aws_region: str = field(default_factory=lambda: (
        os.environ.get("AWS_DEFAULT_REGION") or os.environ.get("AWS_REGION") or "us-east-1"
    ))
    # None → boto3 credential chain (IAM task role); set for ElasticMQ / DynamoDB Local.
    aws_access_key_id: Optional[str] = field(default_factory=lambda: os.environ.get("AWS_ACCESS_KEY_ID"))
    aws_secret_access_key: Optional[str] = field(default_factory=lambda: os.environ.get("AWS_SECRET_ACCESS_KEY"))

    # SQS; set SQS_ENDPOINT_URL for local ElasticMQ
    sqs_endpoint_url: Optional[str] = field(default_factory=lambda: os.environ.get("SQS_ENDPOINT_URL"))
    indexer_queue_url: str = field(default_factory=lambda: os.environ.get(
        "INDEXER_QUEUE_URL", "http://localhost:4100/queue/cmr-indexer-jobs"
    ))

    # Throttler
    rate_per_minute: int = field(default_factory=lambda: int(os.environ.get("RATE_PER_MINUTE", "600")))
    # Granules per page of a collection job; the cursor is saved per page. May exceed
    # rate_per_minute — dispatch_in_batches slices each page to the current rate.
    stream_chunk_size: int = field(default_factory=lambda: int(os.environ.get("STREAM_CHUNK_SIZE", "1000")))

    # Ids per window of a provider job.
    id_range_chunk_size: int = field(default_factory=lambda: int(os.environ.get("ID_RANGE_CHUNK_SIZE", "20000")))
    # Parallel SQS send threads.
    sqs_send_workers: int = field(default_factory=lambda: int(os.environ.get("SQS_SEND_WORKERS", "20")))

    # "oracle" or "stub" (fake data, for unit tests)
    db_backend: str = field(default_factory=lambda: os.environ.get("DB_BACKEND", "oracle"))

    # ACL service for auth validation
    acl_base_url: str = field(default_factory=lambda: os.environ.get("CMR_ACL_BASE_URL", "http://localhost:3011"))
    echo_system_token: str = field(default_factory=lambda: os.environ.get("CMR_ECHO_SYSTEM_TOKEN", "mock-echo-system-token"))

    # DynamoDB job table
    dynamodb_table_name: str = field(default_factory=lambda: os.environ.get("DYNAMODB_JOB_TABLE", "cmr-reindexer-jobs"))
    dynamodb_endpoint_url: Optional[str] = field(default_factory=lambda: os.environ.get("DYNAMODB_ENDPOINT_URL"))

    cancel_check_interval_seconds: int = field(default_factory=lambda: int(os.environ.get("CANCEL_CHECK_INTERVAL_SECONDS", "5")))

    # How stale a running job's heartbeat (its lease) gets before it is restarted.
    lease_minutes: int = field(default_factory=lambda: int(os.environ.get("LEASE_MINUTES", "5")))

    service_name: str = "cmr-reindexer"
    service_version: str = "0.1.0"

    def __post_init__(self) -> None:
        for env_var, value in (
            ("STREAM_CHUNK_SIZE", self.stream_chunk_size),
            ("ID_RANGE_CHUNK_SIZE", self.id_range_chunk_size),
            ("LEASE_MINUTES", self.lease_minutes),
        ):
            if value < 1:
                raise ValueError(f"{env_var} must be a positive integer, got {value}")


config = Config()
