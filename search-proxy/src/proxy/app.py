import hashlib
import logging
import time
import uuid
from contextlib import asynccontextmanager
from urllib.parse import parse_qs

import httpx
import redis.asyncio
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response

from proxy.cache import ResponseCache
from proxy.classifier import classify_request
from proxy.config import ProxySettings, load_lanes_config
from proxy.lanes import LoadSheddingError, RequestLanes

logger = logging.getLogger(__name__)

# Hop-by-hop headers per RFC 2616 §13.5.1
HOP_HEADERS = frozenset(
    {
        "connection",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailers",
        "transfer-encoding",
        "upgrade",
    }
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize shared resources on startup, clean up on shutdown."""
    settings = ProxySettings()
    lanes_config = load_lanes_config(settings.lanes_config)

    app.state.settings = settings
    app.state.lanes_config = lanes_config

    # Connection-pooled httpx client for forwarding requests to the backend
    app.state.backend = httpx.AsyncClient(
        base_url=settings.backend_url,
        timeout=settings.backend_timeout_seconds,
        limits=httpx.Limits(
            max_connections=settings.backend_max_connections,
            max_keepalive_connections=settings.backend_max_keepalive,
        ),
    )
    # Redis connection for distributed lane semaphores and response cache
    app.state.redis = redis.asyncio.from_url(
        settings.redis_url,
        retry_on_timeout=True,
        socket_connect_timeout=settings.redis_socket_connect_timeout,
        socket_timeout=settings.redis_socket_timeout,
        health_check_interval=settings.redis_health_check_interval,
    )
    app.state.lanes = RequestLanes(lanes_config, app.state.redis)
    app.state.cache = ResponseCache(app.state.redis, settings.max_cache_response_bytes)
    yield
    await app.state.backend.aclose()
    await app.state.redis.aclose()


app = FastAPI(title="CMR Search Priority Proxy", lifespan=lifespan)


def _extract_auth_token(request: Request) -> str:
    """Extract and hash the auth token for cache key segmentation.

    Returns a SHA-256 hash so the plaintext token never appears in
    cache keys, logs, or memory beyond this function."""
    token = request.headers.get("Echo-Token") or request.headers.get(
        "Authorization", ""
    )
    if token:
        return hashlib.sha256(token.encode()).hexdigest()[:16]
    return "guest"


def filter_hop_headers(headers: httpx.Headers) -> dict:
    """Remove hop-by-hop headers that must not be forwarded."""
    return {
        name: value
        for name, value in headers.items()
        if name.lower() not in HOP_HEADERS
    }


async def forward_to_backend(
    request: Request,
    path: str,
    request_id: str = "",
) -> httpx.Response:
    """Forward request to the backend, preserving headers, body, and query
    string verbatim. Injects cmr-request-id for log correlation."""
    backend: httpx.AsyncClient = request.app.state.backend

    # Forward all headers except host and content-length, which httpx
    # sets from the base_url and body respectively
    headers = {
        name: value
        for name, value in request.headers.items()
        if name.lower() not in ("host", "content-length")
    }
    if request_id:
        headers["cmr-request-id"] = request_id

    # Append raw query string directly to avoid double-encoding
    query = str(request.url.query)
    url = f"/{path}?{query}" if query else f"/{path}"

    if request.method == "GET":
        return await backend.get(url, headers=headers)

    # POST body is forwarded as raw bytes
    body = await request.body()
    return await backend.request(
        request.method,
        url,
        headers=headers,
        content=body,
    )


# Cached health check result with TTL-based expiration
_health_cache: dict = {"result": None, "expires": 0.0}
_HEALTH_CACHE_TTL = 5.0


@app.get("/health")
async def health(request: Request):
    """Health check matching CMR's {:ok? bool :dependencies {...}} format.

    Each dependency reports ok? and optionally a problem string. Lane
    status is included so the health endpoint doubles as the single
    place to check lane utilization."""
    now = time.monotonic()

    # Return cached result if still valid
    if _health_cache["result"] and now < _health_cache["expires"]:
        cached = _health_cache["result"]
        return JSONResponse(
            status_code=cached["status_code"],
            content=cached["content"],
        )

    dependencies = {}

    # Redis
    try:
        await request.app.state.redis.ping()
        dependencies["redis"] = {"ok?": True}
    except Exception as exc:
        dependencies["redis"] = {"ok?": False, "problem": str(exc)}

    # Backend search service
    try:
        resp = await request.app.state.backend.get("/search/health")
        backend_ok = resp.status_code < 500
        dependencies["search"] = {"ok?": backend_ok}
        if not backend_ok:
            dependencies["search"]["problem"] = f"status {resp.status_code}"
    except Exception as exc:
        dependencies["search"] = {"ok?": False, "problem": str(exc)}

    # Lane utilization
    lanes_config = request.app.state.lanes_config
    redis_client = request.app.state.redis
    for lane in lanes_config.lanes:
        key = f"lane:{lane.name}:active"
        active_raw = await redis_client.get(key)
        active = int(active_raw) if active_raw else 0
        lane_ok = active < lane.permits
        dep = {"ok?": lane_ok, "active": active, "permits": lane.permits}
        if not lane_ok:
            dep["problem"] = "at capacity"
        dependencies[f"lane-{lane.name}"] = dep

    ok = all(dep["ok?"] for dep in dependencies.values())
    status_code = 200 if ok else 503
    content = {"ok?": ok, "dependencies": dependencies}

    _health_cache["result"] = {"status_code": status_code, "content": content}
    _health_cache["expires"] = now + _HEALTH_CACHE_TTL

    return JSONResponse(status_code=status_code, content=content)


def _extract_request_id(request: Request) -> str:
    """Extract or generate a request ID."""
    return (
        request.headers.get("cmr-request-id")
        or request.headers.get("x-request-id")
        or str(uuid.uuid4())
    )


@app.api_route("/{path:path}", methods=["GET", "POST"])
async def proxy(request: Request, path: str):
    """Main proxy handler: classify, cache check, acquire lane, forward."""
    full_path = f"/{path}"

    # Reject oversized POST bodies before reading into memory
    if request.method == "POST":
        content_length = request.headers.get("content-length")
        try:
            claimed_size = int(content_length) if content_length else 0
        except ValueError:
            claimed_size = 0
        if claimed_size > request.app.state.settings.max_request_body_bytes:
            return JSONResponse(
                status_code=413,
                content={"errors": ["Request body too large"]},
                headers={"CMR-Request-Id": _extract_request_id(request)},
            )

        body = await request.body()
        if len(body) > request.app.state.settings.max_request_body_bytes:
            return JSONResponse(
                status_code=413,
                content={"errors": ["Request body too large"]},
                headers={"CMR-Request-Id": _extract_request_id(request)},
            )

    # Merge POST form body params into query params for classification
    content_type = request.headers.get("content-type", "")
    params = dict(request.query_params)

    if request.method == "POST" and "application/x-www-form-urlencoded" in content_type:
        body = await request.body()
        try:
            body_params = parse_qs(body.decode(), keep_blank_values=True)
            for param_name, param_values in body_params.items():
                # Query string params take precedence over body params
                if param_name not in params:
                    params[param_name] = (
                        param_values[0] if len(param_values) == 1 else param_values
                    )
        except UnicodeDecodeError:
            logger.warning(
                "Could not decode POST body as UTF-8, skipping body param extraction"
            )

    request_id = _extract_request_id(request)
    auth_token = _extract_auth_token(request)
    query_string = str(request.url.query)

    # Classify the request into a traffic lane based on query parameters
    lane_name = classify_request(params, content_type)
    lanes: RequestLanes = request.app.state.lanes
    lane = request.app.state.lanes_config.get(lane_name)
    cache: ResponseCache = request.app.state.cache

    # Check cache before acquiring a lane permit
    if lane.cache_ttl > 0:
        try:
            cached = await cache.get(
                request.method, full_path, query_string, auth_token
            )
            if cached:
                response = Response(
                    content=cached["body"],
                    status_code=cached["status_code"],
                    headers=cached.get("headers", {}),
                )
                response.headers["CMR-Request-Id"] = request_id
                return response
        except Exception:
            logger.warning("Cache read failed", exc_info=True)

    # Acquire a distributed semaphore permit for this lane, then forward
    try:
        async with lanes.acquire(lane_name) as actual_lane:
            try:
                backend_response = await forward_to_backend(request, path, request_id)
            except httpx.TimeoutException:
                logger.error(
                    "Backend timeout: %s %s tier=%s",
                    request.method,
                    full_path,
                    actual_lane,
                )
                return JSONResponse(
                    status_code=504,
                    content={"errors": ["Backend timed out"]},
                    headers={"CMR-Request-Id": request_id},
                )
            except httpx.ConnectError:
                logger.error(
                    "Backend unavailable: %s %s tier=%s",
                    request.method,
                    full_path,
                    actual_lane,
                )
                return JSONResponse(
                    status_code=502,
                    content={"errors": ["Backend unavailable"]},
                    headers={"CMR-Request-Id": request_id},
                )

            # Cache successful responses if this lane has a TTL
            if lane.cache_ttl > 0 and backend_response.status_code < 400:
                response_data = {
                    "status_code": backend_response.status_code,
                    "body": backend_response.text,
                    "headers": filter_hop_headers(backend_response.headers),
                }
                try:
                    await cache.set(
                        request.method,
                        full_path,
                        query_string,
                        auth_token,
                        response_data,
                        len(backend_response.content),
                        lane.cache_ttl,
                    )
                except Exception:
                    logger.warning("Cache write failed", exc_info=True)

            # Strip hop-by-hop headers and attach the request ID
            resp_headers = filter_hop_headers(backend_response.headers)
            resp_headers["CMR-Request-Id"] = request_id

            return Response(
                content=backend_response.content,
                status_code=backend_response.status_code,
                headers=resp_headers,
            )

    # Lane is full — no permit available
    except LoadSheddingError as shed_error:
        logger.warning(
            "Load shed: %s %s tier=%s",
            request.method,
            full_path,
            shed_error.lane_name,
        )
        return JSONResponse(
            status_code=429,
            content={
                "errors": [
                    f"Service temporarily overloaded for "
                    f"{shed_error.lane_name}-tier queries"
                ]
            },
            headers={
                "Retry-After": str(shed_error.retry_after),
                "CMR-Request-Id": request_id,
            },
        )
