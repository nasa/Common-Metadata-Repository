# CMR Reindexer

A FastAPI service that drives bulk reindexing of CMR metadata by publishing `concept-update` messages to the CMR indexer's SQS queue. It runs as a single process with a background throttler thread and a background cancellation-cache thread.

## How it works

```
Operator (VPN / VPC only)
         |
         | HTTPS POST /reindex/granules
         v
+-------------------------+
|   Reindexer API         |  ECS Fargate, internal ALB
|   FastAPI               |
+-------------------------+
         |                \
         | 1. query         \ 2. write job record,
         |    providers &    \   heartbeat, checkpoint
         |    collections     v
         v              +------------+
    [Oracle DB]         |  DynamoDB  |
         |              +------------+
         | 3. enqueue         ^
         |    collection      | 5. update total_dispatched
         |    work items      |
         v                    |
+-------------------------+   |
|  Collection Queue SQS   |   |
|  (COLLECTION_QUEUE_URL) |   |
+-------------------------+   |
         |                    |
         | 4. poll            |
         v                    |
+-------------------------+   |
|   Throttler Worker      |---+
|                         |
|  - check ES health      |-----> [Elasticsearch _cluster/health]
|  - stream granule IDs   |-----> [Oracle DB] (keyset pagination)
|  - rate limit output    |
+-------------------------+
         |
         | concept-update messages
         |    (rate limited, ES-green-gated)
         v
+-------------------------+
|  CMR Indexer SQS Queue  |
+-------------------------+
         |
         v
    [CMR Indexer App] --> [Elasticsearch]
```

**Processing flow for granules:**
1. API enqueues one `CollectionWorkItem` per collection onto the collection queue (`COLLECTION_QUEUE_URL`)
2. Throttler reads each `CollectionWorkItem`, opens an Oracle cursor for the collection, and streams granule IDs in chunks via keyset pagination (`fetchmany`)
3. Each chunk is published directly to the indexer queue, rate-limited by a token bucket and gated on ES cluster health
4. A DynamoDB checkpoint is written after each successfully dispatched chunk; SIGTERM or task replacement resumes from the last checkpoint rather than restarting from offset 0

**Job state** is persisted in DynamoDB. If an ECS task is replaced or crashes, the new task resumes any interrupted granule jobs from the last checkpoint on startup.

## API

All write endpoints require an `echo-token` or `Authorization` header with a token that has `INGEST_MANAGEMENT_ACL` update permission.

### Reindex endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/reindex/granules` | Reindex all granules across all providers |
| POST | `/reindex/granules/provider/{provider_id}` | Reindex all granules for one provider |
| POST | `/reindex/granules/collection/{collection_id}` | Reindex all granules for one collection |
| POST | `/reindex/concept/{concept_id}` | Reindex a single concept by CMR concept ID |
| POST | `/reindex/{concept_type}` | Reindex all concepts of a type |

Supported concept types: `variables`, `services`, `tools`, `collections`, `generics`, `data-quality-summaries`, `order-options`, `visualizations`, `subscriptions`, `grids`, `citations`

**Date filtering** (granule endpoints only):

```
POST /reindex/granules?after=2026-07-25T00:00:00Z
POST /reindex/granules?after=2026-07-25T00:00:00Z&before=2026-08-24T00:00:00Z
```

- Datetimes must be ISO8601 UTC with `Z` suffix
- `after` cannot be more than 30 days in the past (returns 400)
- Send `X-CMR-Override-Date-Limit: true` to bypass the 30-day limit
- `after` must be earlier than `before` (returns 400 if not)

All reindex endpoints return `202 Accepted` with a `request_id`:

```json
{"request_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "message": "Reindex started for all providers"}
```

### Job management

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/jobs` | none | List jobs; optional `?status=<status>` filter and `?limit=<1–200>` (default 50) |
| GET | `/jobs/{job_id}` | none | Get job status and progress |
| DELETE | `/jobs/{job_id}` | required | Cancel a running job |

Job record example:

```json
{
  "job_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "running",
  "concept_type": "granules",
  "provider_id": null,
  "collection_id": null,
  "after": null,
  "before": null,
  "providers_to_process": ["PROV_A", "PROV_B"],
  "providers_enqueued": ["PROV_A"],
  "work_items_enqueued": 450,
  "total_dispatched": 12000,
  "last_heartbeat": "2026-08-24T10:30:15Z",
  "started_at": "2026-08-24T10:00:00Z",
  "completed_at": null,
  "elapsed_seconds": 1815,
  "heartbeat_age_seconds": 12,
  "heartbeat_stale": false,
  "dispatch_rate_per_minute": 397
}
```

The `elapsed_seconds`, `heartbeat_age_seconds`, `heartbeat_stale`, and `dispatch_rate_per_minute` fields are computed at query time and not stored in DynamoDB.

Job statuses: `running`, `dispatching`, `completed`, `failed`, `interrupted`, `cancelled`

### Throttle control

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/throttle` | none | Get current rate limit |
| PUT | `/throttle` | required | Update rate limit without restart |

```bash
curl -X PUT http://localhost:8080/throttle \
  -H "echo-token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"rate_per_minute": 300}'
```

### Observability

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/health` | none | Liveness check (used by ALB — no dependency checks) |
| GET | `/status` | none | ES cluster health, queue depths, throttler state |

`/status` response:

```json
{
  "es_health": {"collections": "green", "granules": "green", "overall": "green"},
  "collection_queue_depth": 14230,
  "indexer_queue_depth": 0,
  "throttler_alive": true,
  "throttler_last_active": "2026-08-24T10:30:00Z",
  "throttler_current_job": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "rate_per_minute": 600,
  "tokens_available": 584.3
}
```

## Configuration

All config is via environment variables.

### Required in production

| Variable | Description |
|----------|-------------|
| `COLLECTION_QUEUE_URL` | SQS URL for the collection work-item queue |
| `INDEXER_QUEUE_URL` | SQS URL for the CMR indexer queue |
| `CMR_ACL_BASE_URL` | Base URL of the CMR ACL service |
| `DB_HOST` | Oracle DB host |
| `DB_PORT` | Oracle DB port (default: `1521`) |
| `DB_SERVICE` | Oracle service name (default: `cmr`) |
| `DB_USER` | Oracle username (default: `cmr`) |
| `DB_PASSWORD` | Oracle password |

### Optional

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_BACKEND` | `oracle` | `oracle` (production) or `stub` (unit tests / local dev without Oracle) |
| `DYNAMODB_JOB_TABLE` | `cmr-reindexer-jobs` | DynamoDB table for job tracking |
| `DYNAMODB_CHECKPOINT_TABLE` | `cmr-reindexer-checkpoints` | DynamoDB table for mid-collection resume cursors |
| `DYNAMODB_ENDPOINT_URL` | `None` | DynamoDB endpoint override (docker-compose sets this automatically) |
| `SQS_ENDPOINT_URL` | `None` | SQS endpoint override for local ElasticMQ |
| `CMR_ELASTIC_HOST` | `localhost` | Collections ES host |
| `CMR_ELASTIC_PORT` | `9211` | Collections ES port |
| `CMR_GRAN_ELASTIC_HOST` | `localhost` | Granules ES host |
| `CMR_GRAN_ELASTIC_PORT` | `9210` | Granules ES port |
| `STREAM_CHUNK_SIZE` | `1000` | Granule IDs per Oracle fetchmany call and checkpoint interval |
| `SQS_SEND_WORKERS` | `20` | Parallel threads for batched SQS sends |
| `RATE_PER_MINUTE` | `600` | Indexer queue rate limit (also adjustable live via `PUT /throttle`) |
| `CANCEL_CHECK_INTERVAL_SECONDS` | `5` | How often the cancellation cache refreshes from DynamoDB |
| `STALL_MINUTES` | `20` | Heartbeat age threshold before a job is considered stalled |
| `CMR_ECHO_SYSTEM_TOKEN` | `mock-echo-system-token` | Echo system token used for ACL validation |
| `AWS_DEFAULT_REGION` | `us-east-1` | AWS region |
| `AWS_ACCESS_KEY_ID` | `None` | Explicit AWS key (omit to use IAM task role) |
| `AWS_SECRET_ACCESS_KEY` | `None` | Explicit AWS secret (omit to use IAM task role) |

### DB backends

- **`oracle`** (default) — direct Oracle connection via `oracledb` thin mode (no Oracle Instant Client needed)
- **`stub`** — hardcoded fake data, no external dependencies; used by the unit test suite and local dev without Oracle

## Running tests

All tests use mocked external dependencies — no running services required.

### Install test dependencies

From inside the `cmr-dev` container:

```bash
cd /root/Common-Metadata-Repository/reindexer
pip install -r requirements.txt
pip install pytest pytest-asyncio httpx
```

### Run the full test suite

```bash
pytest tests/ -v
```

### Run a specific test file or test

```bash
pytest tests/test_job_store.py -v
pytest tests/test_routes.py::test_reindex_granules_returns_job_id -v
```

### Test files

| File | Tests | What it covers |
|------|-------|----------------|
| `test_auth.py` | 14 | Token extraction, ACL validation, 401/403/503 responses |
| `test_health_status.py` | 38 | Liveness check, `/status` fields, ES health aggregation, queue-depth failure handling |
| `test_date_validation.py` | 32 | ISO8601 format, ordering, 30-day limit, override header |
| `test_oracle_sql.py` | 28 | SQL table routing by concept type, date clause injection, concept-id prefix routing |
| `test_sqs_client.py` | 27 | Message body shape, hyphenated keys, queue URL routing |
| `test_job_store.py` | 81 | All JobStore methods with mocked DynamoDB |
| `test_cancel_cache.py` | 8 | Cache refresh, is_cancelled lookup, thread stop |
| `test_startup_resume.py` | 15 | Stalled job detection, re-enqueue logic, race condition handling |
| `test_throttler_worker.py` | 58 | Chunk streaming, dispatch, cancellation, SIGTERM/checkpoint behaviour |
| `test_token_bucket.py` | 20 | Rate limiting, set_rate, thread safety |
| `test_routes.py` | 72 | All endpoints, request_id in response, GET/DELETE /jobs |
| `test_throttle_endpoint.py` | 9 | GET/PUT /throttle, auth, rate validation |

### Integration test

`tests/integration_test.py` tests the full end-to-end flow against a live local CMR instance. It requires all services running (metadata-db, ingest, search, Elasticsearch, ElasticMQ, DynamoDB Local, and the reindexer itself).

```bash
pytest tests/integration_test.py -v -s
```

Read the test file for required setup steps (provider creation, queue creation, etc.) before running.
