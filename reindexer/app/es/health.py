import logging
import time

import httpx

from app.config import config

logger = logging.getLogger(__name__)

_STATUS_PRIORITY = {"red": 0, "yellow": 1, "green": 2}


def check_es_health(host: str, port: int) -> str:
    """Returns 'green', 'yellow', or 'red'. Falls back to 'red' on any error."""
    try:
        url = f"http://{host}:{port}/_cluster/health"
        r = httpx.get(url, timeout=5.0)
        return r.json().get("status", "red")
    except Exception as exc:
        logger.warning({"event": "es_health_check_failed", "host": host, "port": port, "error": str(exc)})
        return "red"


def check_all_es_health() -> dict:
    col = check_es_health(config.es_host, config.es_col_port)
    gran = check_es_health(config.es_gran_host, config.es_gran_port)
    overall = col if _STATUS_PRIORITY[col] <= _STATUS_PRIORITY[gran] else gran
    return {"collections": col, "granules": gran, "overall": overall}


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
