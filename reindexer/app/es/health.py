import logging
import threading
import time
from typing import Optional

import httpx

from app.config import config

logger = logging.getLogger(__name__)

_STATUS_PRIORITY = {"red": 0, "yellow": 1, "green": 2}

# Five independent call sites use ES health (the throttler's dispatch loop, GET /status,
# and the ES-gating checks on non-granule and single-concept reindex) — each used to poll
# _cluster/health on both clusters completely independently, with no shared state. Under
# a prolonged non-green cluster (wait_for_green alone polls every 10s) or several of
# these being hit around the same time, that's redundant real traffic against ES for no
# added freshness. Cache the combined result for a short TTL so all consumers share one
# set of checks per window instead of each polling on its own schedule.
_CACHE_TTL_SECONDS = 10.0
_cache_lock = threading.Lock()
_cached_health: Optional[dict] = None
_cached_at: float = 0.0


def check_es_health(host: str, port: int) -> str:
    """Returns 'green', 'yellow', or 'red'. Falls back to 'red' on any error."""
    try:
        url = f"http://{host}:{port}/_cluster/health"
        r = httpx.get(url, timeout=5.0)
        return r.json().get("status", "red")
    except Exception as exc:
        logger.warning({"event": "es_health_check_failed", "host": host, "port": port, "error": str(exc)})
        return "red"


def _check_all_es_health_uncached() -> dict:
    col = check_es_health(config.es_host, config.es_col_port)
    gran = check_es_health(config.es_gran_host, config.es_gran_port)
    overall = col if _STATUS_PRIORITY[col] <= _STATUS_PRIORITY[gran] else gran
    return {"collections": col, "granules": gran, "overall": overall}


def check_all_es_health() -> dict:
    """Cluster health for both ES clusters, shared across all callers for _CACHE_TTL_SECONDS.

    A real health change is still visible to every consumer within one TTL window; this
    only removes the redundant *independent* polling when multiple callers ask within
    the same window.
    """
    global _cached_health, _cached_at
    now = time.monotonic()
    with _cache_lock:
        if _cached_health is not None and (now - _cached_at) < _CACHE_TTL_SECONDS:
            return _cached_health

    # The actual HTTP calls happen outside the lock so a slow/unreachable ES doesn't
    # block other threads from reading the (stale but still valid) cached value in the
    # meantime. Two threads racing past an expired cache both doing one real check is an
    # acceptable, self-limiting cost — far better than every caller polling independently.
    health = _check_all_es_health_uncached()
    with _cache_lock:
        _cached_health = health
        _cached_at = time.monotonic()
    return health


def wait_for_green(
    poll_interval_seconds: float = 10.0,
    timeout_seconds: float = 300.0,
    stop_event=None,
) -> None:
    """Blocks until both ES clusters are green, or stop_event fires (no exception raised on stop)."""
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        health = check_all_es_health()
        if health["overall"] == "green":
            return
        logger.warning({"event": "waiting_for_es_green", "health": health})
        if stop_event is not None:
            if stop_event.wait(timeout=poll_interval_seconds):
                return  # service shutting down
        else:
            time.sleep(poll_interval_seconds)
    raise TimeoutError(f"ES did not reach green within {timeout_seconds}s")
