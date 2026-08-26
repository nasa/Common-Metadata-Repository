# CMR Reindexer

A FastAPI service that drives bulk reindexing of CMR metadata by publishing `concept-update` messages to the CMR indexer's SQS queue. It runs as a single process with a background throttler thread and a background cancellation cache thread.

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
         |    collection      | 6. update total_dispatched
         |    work items      |
         v                    |
+-------------------------+   |
|  cmr-reindexer-jobs SQS |   |
|  (intermediate queue)   |   |
+-------------------------+   |
         |                    |
         | 4. poll            |
         v                    |
+-------------------------+   |
|   Throttler Worker      |---+
|                         |
|  - check ES health      |-----> [Elasticsearch _cluster/health]
|  - split collections    |-----> [Oracle DB] (granule IDs per page)
|    into page items      |
|  - rate limit output    |
+-------------------------+
         |
         | 5. concept-update messages
         |    (rate limited, ES-green-gated)
         v
+-------------------------+
|  CMR Indexer SQS Queue  |
+-------------------------+
         |
         v
    [CMR Indexer App] --> [Elasticsearch]
```

**Two levels of fan-out for granules:**
1. API enqueues one `CollectionWorkItem` per collection onto the intermediate queue
2. Throttler expands each collection into `GranulePageWorkItem`s based on granule count and `CHUNK_SIZE`
3. Throttler fetches each page from Oracle and publishes individual `concept-update` messages to the indexer queue

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

Supported concept types: `variables`, `services`, `tools`, `collections`, `generics`, `data-quality-summaries`, `order-options`, `visualizations`, `subscriptions`, `grid`, `citation`

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
  "completed_at": null
}
```

Job statuses: `running`, `completed`, `failed`, `interrupted`, `cancelled`

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
| GET | `/status` | none | ES cluster health, queue depth, current rate |

`/status` response:

```json
{
  "es_health": {"collections": "green", "granules": "green", "overall": "green"},
  "intermediate_queue_depth": 14230
}
```

Note: the ES health gate requires `green`. For local dev with single-node Elasticsearch, set replicas to 0 (`curl -X PUT "http://localhost:9211/_all/_settings" -d '{"index":{"number_of_replicas":0}}'` and same for port 9210) so the cluster reports green rather than yellow.

## Configuration

All config is via environment variables.

### Required in production

| Variable | Description |
|----------|-------------|
| `INTERMEDIATE_QUEUE_URL` | SQS URL for the cmr-reindexer-jobs queue |
| `INDEXER_QUEUE_URL` | SQS URL for the CMR indexer queue |
| `CMR_ACL_BASE_URL` | Base URL of the CMR ACL service |
| `DB_HOST` | Oracle DB host |
| `DB_PORT` | Oracle DB port (default: `1521`) |
| `DB_SERVICE` | Oracle service name (default: `cmr`) |
| `DB_USER` | Oracle username |
| `DB_PASSWORD` | Oracle password |

### Optional

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_BACKEND` | `oracle` | `oracle` (production) or `stub` (unit tests / local dev without Oracle) |
| `DYNAMODB_JOB_TABLE` | `cmr-reindexer-jobs` | DynamoDB table for job tracking |
| `DYNAMODB_ENDPOINT_URL` | `http://dynamodb-local:8000` | DynamoDB endpoint (docker-compose sets this automatically) |
| `SQS_ENDPOINT_URL` | `None` | Override for local SQS (local dev only) |
| `CMR_ELASTIC_HOST` | `localhost` | Collections ES host |
| `CMR_ELASTIC_PORT` | `9211` | Collections ES port |
| `CMR_GRAN_ELASTIC_HOST` | `localhost` | Granules ES host |
| `CMR_GRAN_ELASTIC_PORT` | `9210` | Granules ES port |
| `CHUNK_SIZE` | `10000` | Granules per page work item |
| `RATE_PER_MINUTE` | `600` | Indexer queue rate limit (also adjustable live via `PUT /throttle`) |
| `CANCEL_CHECK_INTERVAL_SECONDS` | `30` | How often the cancellation cache refreshes |
| `AWS_DEFAULT_REGION` | `us-east-1` | AWS region |
| `AWS_ACCESS_KEY_ID` | `None` | Explicit AWS key (omit to use IAM task role) |
| `AWS_SECRET_ACCESS_KEY` | `None` | Explicit AWS secret (omit to use IAM task role) |

### DB backends

- **`oracle`** (default) — direct Oracle connection via `oracledb` thin mode (no Oracle Instant Client needed)
- **`stub`** — hardcoded fake data, no external dependencies; used by the unit test suite and local dev without Oracle

## Running locally

### Prerequisites

- Docker Desktop running
- The `cmr-dev` Linux container (see repo-level dev workflow docs)
- ElasticMQ running locally for SQS (started via `cmr start local sqs-sns`)
- The CMR dev-system running for Elasticsearch and Redis (`cd dev-system && docker compose up`)

### Start the service

DynamoDB Local is included in `docker-compose.yml` and starts automatically. The reindexer service depends on it so no manual setup is needed.

From inside the `cmr-dev` container after running `sync`:

```bash
cd /root/Common-Metadata-Repository/reindexer
docker compose up
```

The service is available at `http://localhost:8080`.

**First run only** — create the DynamoDB job tracking table:

```bash
aws dynamodb create-table \
  --table-name cmr-reindexer-jobs \
  --attribute-definitions AttributeName=job_id,AttributeType=S \
  --key-schema AttributeName=job_id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --endpoint-url http://localhost:8000 \
  --region us-east-1 \
  --no-sign-request
```

For development with live reload, run uvicorn directly with the same env vars docker-compose sets:

```bash
cd /root/Common-Metadata-Repository/reindexer

DB_BACKEND=stub \
SQS_ENDPOINT_URL=http://host.docker.internal:4100 \
INTERMEDIATE_QUEUE_URL=http://host.docker.internal:4100/queue/cmr-reindexer-jobs \
INDEXER_QUEUE_URL=http://host.docker.internal:4100/queue/cmr-indexer-jobs \
DYNAMODB_ENDPOINT_URL=http://host.docker.internal:8000 \
AWS_ACCESS_KEY_ID=test \
AWS_SECRET_ACCESS_KEY=test \
CMR_ACL_BASE_URL=http://host.docker.internal:3011 \
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

The service is then available at `http://localhost:8000`.

### Iteration workflow

1. Edit files on Windows in VS Code as normal
2. Inside the `cmr-dev` container run `sync` to copy changes into the Linux filesystem
3. `uvicorn` with `--reload` picks up the changes automatically
4. Repeat

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

### Run a specific test file

```bash
pytest tests/test_job_store.py -v
pytest tests/test_throttler_worker.py -v
```

### Run a specific test

```bash
pytest tests/test_routes.py::test_reindex_granules_returns_job_id -v
```

### Test files and what they cover

| File | Tests | What it covers |
|------|-------|----------------|
| `test_auth.py` | 8 | Token extraction, ACL validation, 401/403/503 responses |
| `test_health_status.py` | 28 | Liveness check, status endpoint, ES health aggregation |
| `test_date_validation.py` | 32 | ISO8601 format, ordering, 30-day limit, override header |
| `test_oracle_sql.py` | 17 | SQL table routing by concept type, document_name filters |
| `test_sqs_client.py` | 21 | Message body shape, hyphenated keys, queue URL routing |
| `test_job_store.py` | 27 | All JobStore methods with mocked DynamoDB |
| `test_cancel_cache.py` | 8 | Cache refresh, is_cancelled lookup, thread stop |
| `test_startup_resume.py` | 10 | Stalled job detection, re-enqueue logic, race condition handling |
| `test_throttler_worker.py` | 40 | Page splitting, dispatch, cancellation check, SIGTERM behavior |
| `test_token_bucket.py` | 8 | Rate limiting, set_rate, thread safety |
| `test_routes.py` | 26 | All endpoints, request_id in response, GET/DELETE /jobs |
| `test_throttle_endpoint.py` | 9 | GET/PUT /throttle, auth, rate validation |

### Integration test

`tests/integration_test.py` tests the full end-to-end flow against a live local CMR instance. It requires all services running (metadata-db, ingest, search, Elasticsearch, ElasticMQ, DynamoDB Local, and the reindexer itself).

```bash
pytest tests/integration_test.py -v -s
```

Read the test file for required setup steps (provider creation, queue creation, etc.) before running.
