"""Tracks in-flight Oracle calls, so one that never returns is logged while it hangs.

Kept out of app.db.oracle so it can be imported without the oracledb driver.
"""
import logging
import threading
import time
from contextlib import contextmanager

logger = logging.getLogger(__name__)

# thread ident → (thread name, monotonic start)
_in_flight: dict[int, tuple[str, float]] = {}
_lock = threading.Lock()


@contextmanager
def tracked_call():
    ident = threading.get_ident()
    with _lock:
        _in_flight[ident] = (threading.current_thread().name, time.monotonic())
    try:
        yield
    finally:
        with _lock:
            _in_flight.pop(ident, None)


def log_slow_calls(min_seconds: float) -> None:
    """Log oracle_call_slow for every call in flight for at least min_seconds."""
    now = time.monotonic()
    with _lock:
        calls = list(_in_flight.values())
    for thread, started in calls:
        if now - started >= min_seconds:
            logger.warning({"event": "oracle_call_slow", "thread": thread, "elapsed_seconds": int(now - started)})
