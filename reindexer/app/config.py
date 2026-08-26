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

    # Elasticsearch — two clusters: collections on 9211, granules on 9210
    es_host: str = field(default_factory=lambda: os.environ.get("CMR_ELASTIC_HOST", "localhost"))
    es_col_port: int = field(default_factory=lambda: int(os.environ.get("CMR_ELASTIC_PORT", "9211")))
    es_gran_host: str = field(default_factory=lambda: os.environ.get("CMR_GRAN_ELASTIC_HOST", "localhost"))
    es_gran_port: int = field(default_factory=lambda: int(os.environ.get("CMR_GRAN_ELASTIC_PORT", "9210")))

    # AWS — region falls back through both standard env var spellings
    aws_region: str = field(default_factory=lambda: (
        os.environ.get("AWS_DEFAULT_REGION") or os.environ.get("AWS_REGION") or "us-east-1"
    ))
    # Credentials: None → boto3 uses the credential chain (IAM task role in ECS).
    # Set AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY for local ElasticMQ.
    aws_access_key_id: Optional[str] = field(default_factory=lambda: os.environ.get("AWS_ACCESS_KEY_ID"))
    aws_secret_access_key: Optional[str] = field(default_factory=lambda: os.environ.get("AWS_SECRET_ACCESS_KEY"))

    # SQS — endpoint_url None means real AWS SQS; set SQS_ENDPOINT_URL for local ElasticMQ
    sqs_endpoint_url: Optional[str] = field(default_factory=lambda: os.environ.get("SQS_ENDPOINT_URL"))
    intermediate_queue_url: str = field(default_factory=lambda: os.environ.get(
        "INTERMEDIATE_QUEUE_URL", "http://localhost:4100/queue/cmr-reindexer-jobs"
    ))
    collection_queue_url: str = field(default_factory=lambda: os.environ.get(
        "COLLECTION_QUEUE_URL", "http://localhost:4100/queue/cmr-reindexer-collections"
    ))
    indexer_queue_url: str = field(default_factory=lambda: os.environ.get(
        "INDEXER_QUEUE_URL", "http://localhost:4100/queue/cmr-indexer-jobs"
    ))

    # Throttler
    chunk_size: int = field(default_factory=lambda: int(os.environ.get("CHUNK_SIZE", "10000")))
    rate_per_minute: int = field(default_factory=lambda: int(os.environ.get("RATE_PER_MINUTE", "600")))

    # DB backend: "oracle" (default) or "stub" (hardcoded fake data, for unit tests)
    db_backend: str = field(default_factory=lambda: os.environ.get("DB_BACKEND", "oracle"))

    # ACL service for auth validation
    acl_base_url: str = field(default_factory=lambda: os.environ.get("CMR_ACL_BASE_URL", "http://localhost:3011"))
    echo_system_token: str = field(default_factory=lambda: os.environ.get("CMR_ECHO_SYSTEM_TOKEN", "mock-echo-system-token"))

    # DynamoDB job table
    dynamodb_table_name: str = field(default_factory=lambda: os.environ.get("DYNAMODB_JOB_TABLE", "cmr-reindexer-jobs"))
    dynamodb_endpoint_url: Optional[str] = field(default_factory=lambda: os.environ.get("DYNAMODB_ENDPOINT_URL"))

    # Cancellation cache refresh interval
    cancel_check_interval_seconds: int = field(default_factory=lambda: int(os.environ.get("CANCEL_CHECK_INTERVAL_SECONDS", "5")))

    # Stall detection threshold — must exceed ES wait timeout (300s = 5 min)
    stall_minutes: int = field(default_factory=lambda: int(os.environ.get("STALL_MINUTES", "20")))

    service_name: str = "cmr-reindexer"
    service_version: str = "0.1.0"


config = Config()
