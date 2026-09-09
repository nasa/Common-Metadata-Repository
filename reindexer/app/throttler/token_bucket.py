import threading
import time
from typing import Optional


class TokenBucket:
    """Thread-safe token bucket. Swappable via the interface used by ThrottlerWorker."""

    def __init__(self, rate_per_minute: float):
        self._lock = threading.Lock()
        self._rate_per_second = rate_per_minute / 60.0
        self._max_tokens = float(rate_per_minute)
        self._tokens = self._max_tokens
        self._last_refill = time.monotonic()

    @property
    def current_rate(self) -> float:
        with self._lock:
            return self._rate_per_second * 60.0

    @property
    def tokens_available(self) -> float:
        with self._lock:
            self._refill()
            return self._tokens

    def set_rate(self, rate_per_minute: float) -> None:
        self.update_rate(rate_per_minute)

    def update_rate(self, rate_per_minute: float) -> None:
        with self._lock:
            self._rate_per_second = rate_per_minute / 60.0
            self._max_tokens = float(rate_per_minute)

    def consume(
        self,
        count: int = 1,
        stop_event: Optional[threading.Event] = None,
        cancel_fn=None,
    ) -> bool:
        """Block until 'count' tokens are available, then consume them.

        Returns True if tokens were consumed, False if stop_event fired or
        cancel_fn returned True.  cancel_fn is polled each wake-up interval so
        the caller can abort a long wait (e.g. when a job is cancelled mid-page).
        """
        while True:
            with self._lock:
                self._refill()
                if self._tokens >= count:
                    self._tokens -= count
                    return True
                if self._rate_per_second == 0:
                    wait_for = 1.0
                else:
                    deficit = count - self._tokens
                    wait_for = deficit / self._rate_per_second
            if stop_event is not None:
                if stop_event.wait(timeout=min(wait_for, 1.0)):
                    return False
            else:
                time.sleep(min(wait_for, 1.0))
            if cancel_fn is not None and cancel_fn():
                return False

    def _refill(self) -> None:
        now = time.monotonic()
        self._tokens = min(
            self._max_tokens,
            self._tokens + (now - self._last_refill) * self._rate_per_second,
        )
        self._last_refill = now
