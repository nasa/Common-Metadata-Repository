import logging
import threading
import time
from typing import Optional

import httpx

from app.config import config

logger = logging.getLogger(__name__)

_STATUS_PRIORITY = {"red": 0, "yellow": 1, "green": 2}

# Shared by every caller (scans, /status, reindex endpoints), so they don't each hit ES.
_CACHE_TTL_SECONDS = 10.0
_cache_lock = threading.Lock()
_cached_health: Optional[dict] = None
_cached_at: float = 0.0


def check_es_health(host: str, port: int) -> Optional[str]:
    """None when ES can't be reached or reports an unknown status."""
    try:
        url = f"http://{host}:{port}/_cluster/health"
        status = httpx.get(url, timeout=5.0).json().get("status")
    except Exception as exc:
        logger.warning({"event": "es_health_check_failed", "host": host, "port": port, "error": str(exc)})
        return None
    return status if status in _STATUS_PRIORITY else None


def _check_all_es_health_uncached() -> dict:
    col = check_es_health(config.es_host, config.es_col_port)
    gran = check_es_health(config.es_gran_host, config.es_gran_port)
    overall = None if None in (col, gran) else min(col, gran, key=_STATUS_PRIORITY.get)
    return {"collections": col, "granules": gran, "overall": overall}


def check_all_es_health() -> dict:
    global _cached_health, _cached_at
    now = time.monotonic()
    with _cache_lock:
        if _cached_health is not None and (now - _cached_at) < _CACHE_TTL_SECONDS:
            return _cached_health

    # Check outside the lock so a slow ES doesn't serialize callers; callers arriving
    # after expiry each check until one stores a fresh result.
    health = _check_all_es_health_uncached()
    with _cache_lock:
        _cached_health = health
        _cached_at = time.monotonic()
    return health
