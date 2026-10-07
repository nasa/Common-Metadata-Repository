"""Task-wide rate-limited dispatch for granule scans (one TokenBucket), plus the
cancellation check and shutdown signal every job uses."""
import logging
import threading
from typing import Callable, Optional

from app.config import config
from app.sqs.client import publish_indexer_events_batch
from app.throttler.token_bucket import TokenBucket

logger = logging.getLogger(__name__)


class Throttler:
    def __init__(self) -> None:
        self._stop_event = threading.Event()
        self._token_bucket = TokenBucket(config.rate_per_minute)
        self._cancel_cache = None  # set at startup by main

    def set_cancel_cache(self, cache) -> None:
        self._cancel_cache = cache

    def is_job_cancelled(self, job_id: str) -> bool:
        return bool(self._cancel_cache and self._cancel_cache.is_cancelled(job_id))

    def set_rate(self, rate_per_minute: float) -> None:
        self._token_bucket.update_rate(rate_per_minute)

    def get_rate(self) -> float:
        return self._token_bucket.current_rate

    @property
    def stop_event(self) -> threading.Event:
        """Shutdown signal: scans stop at their next sub-batch or ES poll."""
        return self._stop_event

    def stop(self) -> None:
        if not self._stop_event.is_set():
            self._stop_event.set()
            logger.info({"event": "throttler_stopped"})

    def dispatch_in_batches(
        self,
        records: list[tuple],
        request_id: str,
        on_progress: Optional[Callable[[int], None]] = None,
    ) -> bool:
        """Publish in sub-batches of at most the current rate, re-read each time so PUT
        /throttle applies mid-page. False once shutdown or cancellation interrupts a wait."""
        i = 0
        while i < len(records):
            sub_size = max(1, int(self._token_bucket.current_rate))
            sub = records[i:i + sub_size]
            if not self._token_bucket.consume(
                len(sub), stop_event=self._stop_event, cancel_fn=lambda: self.is_job_cancelled(request_id),
            ):
                return False
            publish_indexer_events_batch(sub, request_id)
            if on_progress is not None:
                on_progress(len(sub))
            i += len(sub)
        return True


throttler = Throttler()
