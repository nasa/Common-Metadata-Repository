"""
Unit tests for TokenBucket: rate changes, consume's stop/cancel exits, and refill.
"""
import threading
import time

import pytest

from app.throttler.token_bucket import TokenBucket


def _drained(rate):
    tb = TokenBucket(rate)
    with tb._lock:
        tb._tokens = 0.0
    return tb


def test_update_rate_changes_rate_and_clamps_tokens():
    tb = TokenBucket(600)
    tb.update_rate(60)
    assert tb.current_rate == pytest.approx(60.0)
    assert tb._tokens <= 60.0


def test_consume_returns_false_when_stopped_during_wait():
    tb = _drained(1)
    stop = threading.Event()
    threading.Timer(0.05, stop.set).start()
    assert tb.consume(1, stop_event=stop) is False


def test_consume_polls_cancel_fn_on_each_wake_up():
    tb = _drained(1)
    calls = []
    assert tb.consume(1, cancel_fn=lambda: calls.append(1) or len(calls) >= 2) is False
    assert len(calls) >= 2


def test_consume_clamps_to_capacity_after_rate_decrease():
    """Without the clamp, a count above the new capacity could never be satisfied."""
    tb = TokenBucket(10_000)
    tb.update_rate(1)
    assert tb.consume(10_000) is True


def test_refill_replenishes_tokens_over_time():
    tb = _drained(600)  # 10 per second
    time.sleep(0.15)
    with tb._lock:
        tb._refill()
        assert 0.5 <= tb._tokens <= 600.0
