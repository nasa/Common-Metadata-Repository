"""In-memory cache of cancelled job IDs, refreshed from DynamoDB in the background."""
import logging
import threading
from typing import Optional

from app.config import config

logger = logging.getLogger(__name__)


class CancelledJobCache:
    def __init__(self, job_store, interval_seconds: Optional[int] = None) -> None:
        self._job_store = job_store
        self._interval = interval_seconds if interval_seconds is not None else config.cancel_check_interval_seconds
        self._cancelled_ids: set = set()
        self._stop_event = threading.Event()
        self._thread: Optional[threading.Thread] = None

    def start(self) -> None:
        self._refresh()
        self._thread = threading.Thread(target=self._run, name="cancel-cache", daemon=True)
        self._thread.start()
        logger.info({"event": "cancel_cache_started", "interval_seconds": self._interval})

    def stop(self) -> None:
        self._stop_event.set()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=10)
        logger.info({"event": "cancel_cache_stopped"})

    def is_cancelled(self, job_id: str) -> bool:
        return job_id in self._cancelled_ids

    def _run(self) -> None:
        while not self._stop_event.wait(timeout=self._interval):
            self._refresh()

    def _refresh(self) -> None:
        try:
            cancelled = self._job_store.find_cancelled_jobs()
            self._cancelled_ids = {job["job_id"] for job in cancelled}
            logger.debug({"event": "cancel_cache_refreshed", "count": len(self._cancelled_ids)})
        except Exception as exc:
            logger.warning({"event": "cancel_cache_refresh_failed", "error": str(exc)})
