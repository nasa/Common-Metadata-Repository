# CMR Reindexer

A FastAPI service that drives bulk reindexing of CMR metadata by publishing `concept-update` (and optionally `concept-delete`) events to the CMR indexer's SQS queue. Each task is a single process with background threads for the collection worker, id-range provider scans, lease renewal, and the cancellation cache.

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
|   Collection Worker     |---+
|                         |
|  - check ES health      |-----> [Elasticsearch _cluster/health]
|  - stream granule IDs   |-----> [Oracle DB] (keyset pagination)
|  - rate limit output    |
+-------------------------+
         |
         | indexer events
         |    (rate limited, ES-green-gated)
         v
+-------------------------+
|  CMR Indexer SQS Queue  |
+-------------------------+
         |
         v
    [CMR Indexer App] --> [Elasticsearch]
```

**Processing flow for granules** (`/reindex/granules`, `/reindex/granules/providers`, `/reindex/granules/collection/{id}`):
1. API enqueues one `CollectionWorkItem` per collection onto the collection queue (`COLLECTION_QUEUE_URL`)
2. The collection worker receives one work item at a time and pages the collection's granule IDs from Oracle via keyset pagination on `concept_id`
3. Each page is published directly to the indexer queue, rate-limited by a token bucket and gated on ES cluster health
4. A DynamoDB checkpoint is written after each page, so a restarted or redelivered work item resumes from the last checkpoint

**Provider runs** (`/reindex/granules/provider/{id}`) skip the collection queue. A scan thread walks the provider's granule table in windows of `ID_RANGE_CHUNK_SIZE` ids and dispatches through the same rate limit; `next_start_id` on the job is its resume point. A granule revised far apart in id space may be dispatched more than once, so `total_dispatched` can exceed the live granule count. At most 5 scans run at once per task.

**Concept-type runs** (`/reindex/{concept_type}`) stream the type's table in a background task.

Providers flagged `small` in `METADATA_DB.providers` have no tables of their own; their concepts live in the shared `SMALL_PROV_*` tables, filtered by `provider_id`.

**Job state** is persisted in DynamoDB. In-progress work is leased by the task doing it: a collection work item by its SQS message visibility, any other job by its `last_heartbeat`. Each task keeps renewing its leases, however long the work takes. If a task dies, its leases lapse after `LEASE_MINUTES` and another task resumes the work from its checkpoint or saved progress. A graceful shutdown hands the collection message back right away. An Oracle call running longer than 5 minutes is logged as `oracle_call_slow` until it returns.

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
- `before` defaults to the request time, so granules revised during the run are left to normal ingest indexing

**Deleted granules** (`?include_deleted=true`, granule endpoints only): granules with a tombstone revision in range also get a `concept-delete`, which removes ES documents left behind by a missed delete. Like bootstrap, this relies on ES versioning: a delete for a granule re-ingested since is ignored. metadata-db keeps tombstones for a year by default, so prefer a provider or collection plus `after`.

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
| DELETE | `/jobs/{job_id}` | required | Cancel any unfinished job (409 if it already finished) |

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
  "collections_split": 210,
  "total_dispatched": 12000,
  "last_heartbeat": "2026-08-24T10:30:15Z",
  "started_at": "2026-08-24T10:00:00Z",
  "completed_at": null,
  "elapsed_seconds": 1815,
  "heartbeat_age_seconds": 12,
  "lease_lapsed": false,
  "avg_dispatch_rate_per_minute": 397,
  "providers_remaining": ["PROV_A", "PROV_B"]
}
```

The `elapsed_seconds`, `heartbeat_age_seconds`, `lease_lapsed`, `avg_dispatch_rate_per_minute`, and `providers_remaining` fields are computed at query time and not stored in DynamoDB.

- `collections_split` / `work_items_enqueued`: collections fully streamed out of those enqueued (collection-queue jobs).
- `next_start_id`: resume point of a provider run.
- `lease_lapsed`: only on heartbeat-leased jobs (`running` jobs and `dispatching` provider runs). `true` means no task has renewed it for `LEASE_MINUTES`; the lease keeper restarts it, or fails it if it can't be restarted (e.g. a single-concept request).
- `heartbeat_age_seconds`: on a collection-queue job, only refreshed while one of its work items is streaming, so it grows while the job waits in the queue.
- `avg_dispatch_rate_per_minute`: averaged over the job's lifetime, including time spent queued.
- `providers_remaining` (`granules` / `granules-by-providers` jobs): providers never enqueued, or with collections not yet streamed. The set to resubmit after a cancel.
- `include_deleted`: present on jobs started with that flag.

Job statuses: `running`, `dispatching`, `completed`, `failed`, `cancelled`

### Throttle control

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/throttle` | none | Get current rate limit |
| PUT | `/throttle` | required | Update rate limit without restart |

The rate limit is per task and shared by every job on that task. `PUT /throttle` changes only the task the request reaches, and only until that task restarts; set `RATE_PER_MINUTE` for a lasting change.

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
| GET | `/status` | none | What the answering task is working on, ES cluster health, queue counts |

```bash
curl -s http://localhost:8080/reindexer/health
curl -s http://localhost:8080/reindexer/status
```

`/status` response:

```json
{
  "task": {
    "id": "ip-10-4-210-36.ec2.internal",
    "jobs_in_progress": ["3fa85f64-5717-4562-b3fc-2c963f66afa6", "9aeb1a64-2379-497c-8f91-69fb0b72600b"],
    "collection_worker": {"alive": true, "current_job": "3fa85f64-5717-4562-b3fc-2c963f66afa6"},
    "rate_limit_per_minute": 600
  },
  "es_health": {"collections": "green", "granules": "green", "overall": "green"},
  "queues": {
    "collection": {"available": 14229, "in_flight": 1},
    "indexer": {"available": 0, "in_flight": 0}
  }
}
```

- `task` describes only the task that answered. With more than one task (e.g. during a deploy), successive calls can reach different tasks.
- `jobs_in_progress`: every job this task is working on, including provider runs and concept-type runs, which don't use the collection worker.
- `collection_worker.current_job`: the collection-queue job being streamed, or `null` when idle.
- `queues`: SQS approximate counts. `in_flight` messages are received but not yet finished; a queue SQS can't report is `null`.

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
| `STREAM_CHUNK_SIZE` | `1000` | Concepts per page of the per-collection granule scan, and the checkpoint interval |
| `ID_RANGE_CHUNK_SIZE` | `20000` | Ids per id-range scan window for `POST /reindex/granules/provider/{id}` |
| `SQS_SEND_WORKERS` | `20` | Parallel threads for batched SQS sends |
| `RATE_PER_MINUTE` | `600` | Indexer queue rate limit per task (adjustable live via `PUT /throttle`) |
| `CANCEL_CHECK_INTERVAL_SECONDS` | `5` | How often the cancellation cache refreshes from DynamoDB |
| `LEASE_MINUTES` | `5` | Lease on in-progress work: the collection message's visibility, and how stale a job's heartbeat gets before another task restarts it (and `/jobs` reports `lease_lapsed`) |
| `CMR_ECHO_SYSTEM_TOKEN` | `mock-echo-system-token` | Echo system token used for ACL validation |
| `ORACLE_POOL_MIN` / `ORACLE_POOL_MAX` / `ORACLE_POOL_INCREMENT` | `2` / `15` / `1` | Oracle connection pool sizing, shared by API requests and background work |
| `AWS_DEFAULT_REGION` | `us-east-1` | AWS region (falls back to `AWS_REGION`) |
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
pytest tests/test_routes.py::TestJobEnrichment -v
```

### Integration test

`tests/integration_test.py` tests the full end-to-end flow against a live local CMR instance. It requires all services running (metadata-db, ingest, search, Elasticsearch, ElasticMQ, DynamoDB Local, and the reindexer itself).

```bash
PYTHONPATH=. python3 tests/integration_test.py
```

Read the test file for required setup steps (provider creation, queue creation, etc.) before running.
