# CMR Search Proxy

A traffic-shaping proxy that sits in front of CMR search. It classifies incoming requests into priority lanes, enforces concurrency limits via Redis-backed distributed semaphores, and caches responses to reduce backend load.

## How it works

Every request is classified into one of three lanes based on query complexity:

| Lane | Permits | Cache TTL | Overflow | Retry-After |
|------|---------|-----------|----------|-------------|
| express | 200 | 10s | standard | 5s |
| standard | 150 | 15s | — | 5s |
| heavy | 50 | 30s | — | 10s |

**Classification rules** (first match wins):

- **Heavy**: `include_facets`, `online_only`, `cloud_cover`, temporal facet params (`temporal_facet[`), cycle/pass params (`cycle[`, `passes[`), `options[readable_granule_name][pattern]`, `options[granule_ur][pattern]`, `options[producer_granule_id][pattern]`, shapefile uploads, `polygon[]` (multi-polygon, always heavy), single `polygon` with >20 vertices, bounding boxes with area >5000 sq degrees, more than 2 bounding boxes (`bounding_box[]` with 3+ values)
- **Standard**: `temporal`, `updated_since`, `revision_date`, `orbit_number`, `point`, `point[]`, single `circle`, small polygon (≤20 vertices), small bounding box (≤5000 sq degrees)
- **Express**: `circle[]` (explicit fast path — always express regardless of other params), and everything not matched above

**Concurrency**: each lane has a Redis sorted set (`lane:{name}:active`). When a request arrives, expired entries are pruned, the active count is checked against the permit limit, and if under the limit the request is added as a member scored by its expiry epoch. If the lane is full, the request either overflows to the configured overflow lane or is rejected with a 429. The entry is removed when the request completes. Entries whose score has passed are pruned automatically on the next acquire, so permits from crashed tasks recover without manual intervention.

**Cache**: successful (2xx) responses are stored in Redis keyed on a SHA-256 hash of method, path, query string, hashed auth token, `Accept` header, `cmr-search-after` header, and POST body. Cache hits skip lane acquisition entirely.

**Load shedding response**:
```
HTTP 429 Too Many Requests
Retry-After: 10

{"errors": ["Service temporarily overloaded for heavy-tier queries"]}
```

## Configuration

All settings are environment variables with the `CMR_PROXY_` prefix.

| Variable | Default | Description |
|----------|---------|-------------|
| `CMR_PROXY_BACKEND_URL` | _none — required, startup fails if unset_ | CMR search base URL (no `/search` suffix) |
| `CMR_PROXY_REDIS_URL` | _none — required, startup fails if unset_ | Redis connection URL |
| `CMR_PROXY_LANES_CONFIG` | `lanes.json` | Path to lanes config file; used when `CMR_PROXY_LANES_JSON` is not set |
| `CMR_PROXY_LANES_JSON` | — | Lanes config as a JSON string; takes precedence over `CMR_PROXY_LANES_CONFIG` when set. Intended for deployments that inject the value from Parameter Store as an environment variable |
| `CMR_PROXY_LOG_LEVEL` | `INFO` | Log level (`DEBUG`, `INFO`, `WARNING`) |
| `CMR_PROXY_MAX_REQUEST_BODY_BYTES` | `52428800` | Max POST body size (50MB) |
| `CMR_PROXY_MAX_CACHE_RESPONSE_BYTES` | `1048576` | Max response size to cache (1MB) |
| `CMR_PROXY_BACKEND_TIMEOUT_SECONDS` | `300.0` | Backend request timeout |
| `CMR_PROXY_BACKEND_MAX_CONNECTIONS` | `500` | httpx connection pool size |
| `CMR_PROXY_BACKEND_MAX_KEEPALIVE` | `200` | httpx keepalive connection pool size |
| `CMR_PROXY_REDIS_MAX_CONNECTIONS` | auto | Redis pool size; defaults to total lane permits + 100 |
| `CMR_PROXY_REDIS_SOCKET_CONNECT_TIMEOUT` | `2.0` | Redis connection timeout in seconds |
| `CMR_PROXY_REDIS_SOCKET_TIMEOUT` | `2.0` | Redis read/write timeout in seconds |
| `CMR_PROXY_REDIS_HEALTH_CHECK_INTERVAL` | `30` | Seconds between Redis keepalive pings |

### Feature toggles

| Variable | Default | Description |
|----------|---------|-------------|
| `CMR_PROXY_BYPASS_ENABLED` | `false` | Skip classification, cache, and lanes — pure transparent proxy |
| `CMR_PROXY_CACHE_ENABLED` | `true` | Enable response caching |
| `CMR_PROXY_LOAD_SHEDDING_ENABLED` | `true` | Return 429 when lanes are full; when false, requests proceed over capacity but are still counted in the sorted set so pressure remains visible in `/health` |
| `CMR_PROXY_CLASSIFICATION_ENABLED` | `true` | Classify requests; when false, all traffic routes to the default lane |

## Lanes configuration

Lane definitions live in `lanes.json`. Each lane supports:

```json
{
  "name": "express",
  "permits": 200,
  "overflow": "standard",
  "cache_ttl": 10,
  "retry_after": 5,
  "default": true
}
```

- `permits` — maximum concurrent in-flight requests
- `overflow` — lane to try if this one is full (optional)
- `cache_ttl` — response cache TTL in seconds (0 disables caching)
- `retry_after` — value of the `Retry-After` header on 429 responses
- `default` — exactly one lane must be marked as the default

## Health endpoints

### `GET /health/shallow`

Always returns HTTP 200. Used for ALB/ECS target group health checks so that Redis or backend failures do not trigger task replacement.

### `GET /health`

Informational health check, not cached. Nothing automated polls it — ALB/ECS use `/health/shallow`. Currently always returns HTTP 200: dependencies report their status but do not affect the top-level `ok?`.

```json
{
  "ok?": true,
  "dependencies": {
    "redis": {"ok?": true},
    "search": {"ok?": true, "reachable": true},
    "lane-express": {"ok?": true, "active": 12, "permits": 200, "at_capacity": false},
    "lane-standard": {"ok?": true, "active": 3, "permits": 150, "at_capacity": false},
    "lane-heavy": {"ok?": true, "active": 0, "permits": 50, "at_capacity": false}
  }
}
```

When a lane is at capacity, `at_capacity` is `true` but `ok?` remains `true`. Use this endpoint to monitor lane utilization rather than to drive automated remediation.

## Running locally

Requires Python 3.11+ (`pyproject.toml` sets `requires-python = ">=3.11"`).
Deploys run on `python:3.11-slim` and `ruff` targets `py311`, so develop on
3.11 to match — on macOS, `brew install python@3.11`. Use a virtualenv:

```bash
python3.11 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install -e ".[dev]"

# Start Redis
docker run -d -p 6379:6379 redis

# Run the proxy
CMR_PROXY_BACKEND_URL=http://localhost:3003 \
CMR_PROXY_REDIS_URL=redis://localhost:6379 \
uvicorn proxy.app:app --port 8080
```

Requests to `http://localhost:8080/search/collections` are proxied to the backend at `http://localhost:3003/search/collections`.

## Running tests

```bash
pip install -e ".[dev]"
pytest
```

## Operational notes

**Leaked permits**: A permit leaks when a task is killed before `_release` runs, or when Redis is briefly unavailable during release (the exception is swallowed so the ASGI handler can still return a response). Once a leaked entry's TTL score passes (defaulting to `backend_timeout_seconds`, 300 seconds), it stops affecting lane counts — the health endpoint's `ZCOUNT` filters on the current timestamp as a lower bound, and each acquire's `ZCARD` runs after `ZREMRANGEBYSCORE` prunes expired-score entries. Physical removal from Redis happens on the next acquire for that lane. Note: if Redis is unavailable during acquire, the fail-open path applies — no permit is stored and no release is attempted, so there is no leak in that case. To immediately reset a lane without waiting for TTL, delete its sorted set key from Redis: `lane:express:active`, `lane:standard:active`, `lane:heavy:active`.

**Debugging**: Set `CMR_PROXY_LOG_LEVEL=DEBUG` to log backend response details including content encoding and actual byte counts. Remove when done — debug logging is verbose under load.
