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
| POST | `/reindex/granules/providers` | Reindex all granules for a list of providers (JSON body) |
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

```bash
curl -X POST http://localhost:8080/reindexer/reindex/citations \
  -H "Authorization: $TOKEN"
```

```json
{"request_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "message": "Reindex started for concept type citations"}
```

`/reindex/granules/providers` takes the provider list as a JSON body:

```bash
curl -X POST "http://localhost:8080/reindexer/reindex/granules/providers?after=2026-07-25T00:00:00Z" \
  -H "Authorization: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"provider_ids": ["PROV_A", "PROV_B"]}'
```

### Job management

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/jobs` | none | List jobs; optional `?status=<status>` filter and `?limit=<1–200>` (default 50) |
| GET | `/jobs/{job_id}` | none | Get job status and progress |
| DELETE | `/jobs/{job_id}` | required | Cancel a running job |

Examples:

```bash
# List recent jobs
curl -s http://localhost:8080/reindexer/jobs?status=running

# Get a specific job
curl -s http://localhost:8080/reindexer/jobs/3fa85f64-5717-4562-b3fc-2c963f66afa6

# Cancel a job
curl -X DELETE http://localhost:8080/reindexer/jobs/3fa85f64-5717-4562-b3fc-2c963f66afa6 \
  -H "Authorization: $TOKEN"
```

Job record example:

```json
{
  "job_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "running",
  "concept_type": "granules",
  "source_url": "/reindexer/reindex/granules?after=2026-08-01T00:00:00Z",
  "providers_requested": ["PROV_A", "PROV_B"],
  "providers_enqueued": ["PROV_A"],
  "providers_work_items": {"PROV_A": 300},
  "providers_collections_split": {"PROV_A": 210},
  "work_items_enqueued": 450,
  "total_dispatched": 12000,
  "last_heartbeat": "2026-08-24T10:30:15Z",
  "started_at": "2026-08-24T10:00:00Z",
  "completed_at": null,
  "elapsed_seconds": 1815,
  "heartbeat_age_seconds": 12,
  "heartbeat_stale": false,
  "dispatch_rate_per_minute": 397,
  "providers_remaining": ["PROV_A", "PROV_B"]
}
```

The `elapsed_seconds`, `heartbeat_age_seconds`, `heartbeat_stale`, `dispatch_rate_per_minute`, and `providers_remaining` fields are computed at query time and not stored in DynamoDB.

`providers_remaining` (only present on `granules`/`granules-by-providers` jobs, which track `providers_requested`) lists every provider that still has outstanding work — either never enqueued at all, or enqueued but not every one of its collections has finished streaming yet (`providers_collections_split[p] < providers_work_items[p]`). This is the safe set to resubmit via `POST /reindex/granules/providers` after cancelling a job, without needing to inspect the checkpoint table.

Job statuses: `running`, `dispatching`, `completed`, `failed`, `interrupted`, `cancelled`

### Throttle control

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/throttle` | none | Get current rate limit |
| PUT | `/throttle` | required | Update rate limit without restart |

```bash
# Get current rate
curl -s http://localhost:8080/reindexer/throttle

# Update rate
curl -X PUT http://localhost:8080/reindexer/throttle \
  -H "Authorization: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"rate_per_minute": 300}'
```

### Observability

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/health` | none | Liveness check (used by ALB — no dependency checks) |
| GET | `/status` | none | ES cluster health, queue depths, throttler state |

```bash
curl -s http://localhost:8080/reindexer/health
curl -s http://localhost:8080/reindexer/status
```

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

### Integration test

`tests/integration_test.py` tests the full end-to-end flow against a live local CMR instance. It requires all services running (metadata-db, ingest, search, Elasticsearch, ElasticMQ, DynamoDB Local, and the reindexer itself).

```bash
pytest tests/integration_test.py -v -s
```

Read the test file for required setup steps (provider creation, queue creation, etc.) before running.
