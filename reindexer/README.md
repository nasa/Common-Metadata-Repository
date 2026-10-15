# CMR Reindexer

A FastAPI service that drives bulk reindexing of CMR metadata by publishing `concept-update` and `concept-delete` events to the CMR indexer's SQS queue. Each task is a single process with background threads for granule scans, lease renewal, and the cancellation cache.

## How it works

```
Operator (VPN / VPC only)
         |
         | HTTPS POST /reindex/granules
         v
+-------------------------+        job record, scan cursor,
|   Reindexer API         |        heartbeat, total_dispatched
|   FastAPI               |------------------> [DynamoDB]
|                         |
|   scan threads          |-----> [Oracle DB]  (granule ids, by id window or concept_id page)
|   - wait for ES green   |-----> [Elasticsearch _cluster/health]
|   - rate limit output   |
+-------------------------+
         |
         | indexer events
         v
+-------------------------+
|  CMR Indexer SQS Queue  |
+-------------------------+
         |
         v
    [CMR Indexer App] --> [Elasticsearch]
```

**Granule runs** each start one scan thread, which publishes straight to the indexer queue through the task's rate limit, pausing while ES is not green. At most 5 scans run at once per task; the rest wait for a slot.
- **Provider runs** (`/reindex/granules`, `/reindex/granules/providers`, `/reindex/granules/provider/{id}`) walk each provider's granule table in turn, in windows of `ID_RANGE_CHUNK_SIZE` ids, the way bootstrap does. The scan looks up the next matching id (`MIN(id)`, date-filtered on a dated run) at the start and after each empty window, so it skips stretches with nothing in range. A granule revised far apart in id space may be dispatched more than once, so `total_dispatched` can exceed the live granule count.
- **Collection runs** (`/reindex/granules/collection/{id}`) page the collection's granules by `concept_id`, `STREAM_CHUNK_SIZE` at a time, publishing each granule's latest revision.

After each window or page the scan saves its cursor on the job, which is where a restarted job resumes.

**Concept-type runs** (`/reindex/{concept_type}`) stream the type's table in a background task, sending a `concept-update` for each live concept. They are not rate limited, and fail at the start if ES is not green.

Providers flagged `small` in `METADATA_DB.providers` share the `SMALL_PROV_*` tables. Granule reindexing is disabled for them: `/reindex/granules` skips them (logged as `small_providers_skipped`), and the other granule endpoints, including `/reindex/concept/G…`, return 400.

**Job state** is persisted in DynamoDB. A running job is leased by the task doing it, through its `last_heartbeat`, which that task keeps renewing however long the work takes. If a task dies, its leases lapse after `LEASE_MINUTES` and another task restarts the job: a granule run from its cursor, a concept-type run from the start.

An Oracle call running longer than 5 minutes is logged as `oracle_call_slow` until it returns.

## API

All write endpoints require an `echo-token` or `Authorization` header with a token that has system-level `INGEST_MANAGEMENT_ACL` update permission (provider-level grants don't count).

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

- Datetimes must be `YYYY-MM-DDTHH:MM:SSZ` (UTC, no fractional seconds)
- `after` cannot be more than 30 days in the past (returns 400)
- Send `X-CMR-Override-Date-Limit: true` to bypass the 30-day limit
- `after` must be earlier than `before` (returns 400 if not)
- `before` defaults to the request time, so granules revised during the run are left to normal ingest indexing

**Deleted granules**: like bootstrap, granule runs send a `concept-delete` for each tombstone in range, which removes ES documents left behind by a missed delete. ES versioning makes stale events no-ops, e.g. a delete for a granule re-ingested since.

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
| GET | `/jobs` | none | List jobs (unsorted); optional `?status=<status>` filter and `?limit=<1–200>` (default 50) |
| GET | `/jobs/{job_id}` | none | Get job status and progress |
| DELETE | `/jobs/{job_id}` | required | Cancel any unfinished job (409 if it already finished) |

Examples:

```bash
# List running jobs
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
  "after": "2026-08-01T00:00:00Z",
  "before": "2026-08-24T10:00:00Z",
  "providers_requested": ["PROV_A", "PROV_B"],
  "providers_done": ["PROV_A"],
  "scan_provider": "PROV_B",
  "scan_cursor": 184000000,
  "total_dispatched": 12000,
  "last_heartbeat": "2026-08-24T10:30:15Z",
  "started_at": "2026-08-24T10:00:00Z",
  "elapsed_seconds": 1815,
  "heartbeat_age_seconds": 12,
  "lease_lapsed": false,
  "avg_dispatch_rate_per_minute": 397,
  "providers_remaining": ["PROV_B"]
}
```

The `elapsed_seconds`, `heartbeat_age_seconds`, `lease_lapsed`, `avg_dispatch_rate_per_minute`, and `providers_remaining` fields are computed at query time and not stored in DynamoDB.

- `concept_type`: `granules`, `granules-by-provider`, `granules-by-providers`, `granules-by-collection`, `concept`, or the route's type (e.g. `citations`).
- `scan_cursor`: where the scan resumes. On a provider run it is an id in `scan_provider`'s table; on a collection run, the last `concept_id` paged.
- `lease_lapsed`: only on `running` jobs. `true` means no task has renewed it for `LEASE_MINUTES`; the lease keeper restarts it, or fails it if it can't be restarted (a single-concept request).
- `providers_remaining`: providers not yet finished, in scan order. The set to resubmit after a cancel.

Job statuses: `running`, `completed`, `failed`, `cancelled`

### Throttle control

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/throttle` | none | Get current rate limit |
| PUT | `/throttle` | required | Update rate limit without restart |

The rate limit applies to granule runs; it is per task and shared by every granule run on that task. `PUT /throttle` changes only the task the request reaches, and only until that task restarts; set `RATE_PER_MINUTE` for a lasting change.

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
| GET | `/status` | none | What the answering task is working on, ES cluster health, indexer queue counts |

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
    "rate_limit_per_minute": 600
  },
  "es_health": {"collections": "green", "granules": "green", "overall": "green"},
  "indexer_queue": {"available": 0, "in_flight": 0}
}
```

- `es_health`: a cluster that can't be reached is `null`, and so is `overall`; anything but `green` pauses granule runs.
- `task` describes only the task that answered. With more than one task (e.g. during a deploy), successive calls can reach different tasks.
- `jobs_in_progress`: every job this task holds the lease on, including scans waiting for a slot.
- `indexer_queue`: SQS approximate counts; `in_flight` messages are received by the indexer but not yet finished. `null` when SQS can't report them.

## Configuration

All config is via environment variables.

### Required in production

| Variable | Description |
|----------|-------------|
| `INDEXER_QUEUE_URL` | SQS URL for the CMR indexer queue |
| `CMR_ACL_BASE_URL` | Base URL of the CMR ACL service |
| `CMR_ECHO_SYSTEM_TOKEN` | Echo system token used for ACL lookups |
| `CMR_ELASTIC_HOST` / `CMR_GRAN_ELASTIC_HOST` | Collections / granules ES hosts |
| `DB_HOST` | Oracle DB host |
| `DB_PASSWORD` | Oracle password |

### Optional

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_BACKEND` | `oracle` | `oracle` (oracledb thin mode, no Instant Client) or `stub` (fake data, for unit tests) |
| `DB_PORT` / `DB_SERVICE` / `DB_USER` | `1521` / `cmr` / `cmr` | Oracle connection |
| `DYNAMODB_JOB_TABLE` | `cmr-reindexer-jobs` | DynamoDB table for job tracking |
| `DYNAMODB_ENDPOINT_URL` | `None` | DynamoDB endpoint override (docker-compose sets this automatically) |
| `SQS_ENDPOINT_URL` | `None` | SQS endpoint override for local ElasticMQ |
| `CMR_ELASTIC_PORT` / `CMR_GRAN_ELASTIC_PORT` | `9211` / `9210` | Collections / granules ES ports |
| `STREAM_CHUNK_SIZE` | `1000` | Granules per page of a collection run |
| `ID_RANGE_CHUNK_SIZE` | `20000` | Ids per window of a provider run |
| `SQS_SEND_WORKERS` | `20` | Parallel threads for batched SQS sends |
| `RATE_PER_MINUTE` | `600` | Granule-run events per minute, per task (adjustable live via `PUT /throttle`) |
| `CANCEL_CHECK_INTERVAL_SECONDS` | `5` | How often a task with running jobs checks DynamoDB for cancellations |
| `LEASE_MINUTES` | `5` | How stale a running job's heartbeat gets before another task restarts it (and `/jobs` reports `lease_lapsed`) |
| `ORACLE_POOL_MIN` / `ORACLE_POOL_MAX` / `ORACLE_POOL_INCREMENT` | `2` / `15` / `1` | Oracle connection pool sizing, shared by API requests and background work |
| `AWS_DEFAULT_REGION` | `us-east-1` | AWS region (falls back to `AWS_REGION`) |
| `AWS_ACCESS_KEY_ID` | `None` | Explicit AWS key (omit to use IAM task role) |
| `AWS_SECRET_ACCESS_KEY` | `None` | Explicit AWS secret (omit to use IAM task role) |

The DynamoDB job table (hash key `job_id`, string) must already exist; the service doesn't create it. Finished jobs get a `ttl` 30 days out; enable TTL on that attribute to expire them.

## Running tests

Unit tests mock all external services. From `reindexer/`:

```bash
pip install -r requirements.txt pytest
pytest tests/
```

`tests/integration_test.py` runs the end-to-end flow against a live local CMR; its docstring lists the services it needs.

```bash
PYTHONPATH=. python3 tests/integration_test.py
```
