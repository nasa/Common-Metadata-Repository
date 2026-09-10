"""
Unit tests for TokenBucket.

Exercises rate reading, rate updating, and set_rate/update_rate equivalence.
The consume() blocking behaviour is not tested here to avoid slow tests;
that logic is covered implicitly by the throttler worker tests.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_token_bucket.py -v
"""
import pytest

from app.throttler.token_bucket import TokenBucket


class TestTokenBucket:

    def test_initial_rate_matches_constructor(self):
        tb = TokenBucket(600)
        assert tb.current_rate == pytest.approx(600.0)

    def test_set_rate_updates_current_rate(self):
        tb = TokenBucket(600)
        tb.set_rate(300)
        assert tb.current_rate == pytest.approx(300.0)

    def test_update_rate_updates_current_rate(self):
        tb = TokenBucket(600)
        tb.update_rate(1200)
        assert tb.current_rate == pytest.approx(1200.0)

    def test_set_rate_and_update_rate_are_equivalent(self):
        tb1 = TokenBucket(600)
        tb2 = TokenBucket(600)
        tb1.set_rate(400)
        tb2.update_rate(400)
        assert tb1.current_rate == tb2.current_rate

    def test_rate_stored_in_per_second_internally(self):
        tb = TokenBucket(120)
        # 120 per minute == 2.0 per second
        assert tb._rate_per_second == pytest.approx(2.0)

    def test_set_rate_changes_internal_rate_per_second(self):
        tb = TokenBucket(600)
        tb.set_rate(60)
        assert tb._rate_per_second == pytest.approx(1.0)

    def test_high_rate_consume_returns_immediately(self):
        tb = TokenBucket(10_000_000)  # 10M/min — bucket starts full
        tb.consume(1)  # should not block

    def test_current_rate_after_multiple_updates(self):
        tb = TokenBucket(100)
        tb.set_rate(200)
        tb.set_rate(300)
        assert tb.current_rate == pytest.approx(300.0)

    def test_consume_returns_true_when_tokens_available(self):
        tb = TokenBucket(10_000_000)
        assert tb.consume(1) is True

    def test_consume_with_stop_event_returns_true_when_tokens_available(self):
        import threading
        tb = TokenBucket(10_000_000)
        stop = threading.Event()
        assert tb.consume(1, stop_event=stop) is True

    def test_consume_with_stop_event_already_set_returns_false(self):
        import threading
        tb = TokenBucket(1)  # very slow — drain so next consume must wait
        tb.consume(1)        # drain the bucket
        stop = threading.Event()
        stop.set()
        result = tb.consume(1, stop_event=stop)
        assert result is False

    def test_tokens_available_returns_float(self):
        tb = TokenBucket(600)
        assert isinstance(tb.tokens_available, float)

    def test_tokens_available_decreases_after_consume(self):
        tb = TokenBucket(10_000_000)
        before = tb.tokens_available
        tb.consume(100)
        assert tb.tokens_available < before

    def test_tokens_available_does_not_exceed_max(self):
        tb = TokenBucket(600)
        assert tb.tokens_available <= 600.0

    def test_consume_with_stop_event_fired_during_wait_returns_false(self):
        import threading
        tb = TokenBucket(1)  # very slow — drain so next consume must wait
        tb.consume(1)        # drain the bucket
        stop = threading.Event()
        t = threading.Timer(0.05, stop.set)
        t.start()
        result = tb.consume(1, stop_event=stop)
        assert result is False

    def test_consume_with_cancel_fn_true_returns_false(self):
        """cancel_fn returning True on the first poll causes consume() to return False."""
        tb = TokenBucket(1)  # very slow — drain so next consume must wait
        tb.consume(1)        # drain the bucket
        result = tb.consume(1, cancel_fn=lambda: True)
        assert result is False

    def test_consume_with_cancel_fn_false_does_not_abort(self):
        """cancel_fn returning False has no effect when tokens are plentiful."""
        tb = TokenBucket(10_000_000)
        result = tb.consume(1, cancel_fn=lambda: False)
        assert result is True

    def test_consume_cancel_fn_polled_on_each_wake_up(self):
        """cancel_fn is called on successive wake-ups until it returns True."""
        tb = TokenBucket(1)
        tb.consume(1)  # drain the bucket so subsequent consumes must wait
        call_count = [0]

        def cancel_fn():
            call_count[0] += 1
            return call_count[0] >= 2  # allow one sleep, abort on the second

        result = tb.consume(1, cancel_fn=cancel_fn)
        assert result is False
        assert call_count[0] >= 2

    def test_consume_clamps_to_max_tokens_after_rate_decrease(self):
        """consume() never deadlocks when count > max_tokens due to a mid-run rate decrease."""
        tb = TokenBucket(10_000)  # starts full at 10_000 tokens
        tb.update_rate(1)         # rate decreased: max_tokens drops to 1, tokens clamped to 1
        # Without clamping: consume(10_000) would deadlock (10_000 > max_tokens=1).
        # With clamping: effective=min(10_000, 1)=1; bucket holds 1 token → returns immediately.
        result = tb.consume(10_000)
        assert result is True

    def test_refill_replenishes_tokens_over_time(self):
        """_refill math: tokens accumulate at rate_per_second after a drain."""
        import time
        # 600 per minute == 10 per second.  Drain the bucket, wait 0.15s, expect ~1.5 tokens back.
        tb = TokenBucket(600)
        tb.consume(1)  # drain the single starting token (max_tokens=600, starts full → drain one)
        # Force-drain entirely so we're starting from 0 (set internal state directly)
        with tb._lock:
            tb._tokens = 0.0

        time.sleep(0.15)

        available = tb.tokens_available
        # At 10 tok/s for 0.15s we expect ~1.5 tokens; allow a wide tolerance for CI scheduling
        assert available >= 0.5, f"expected ≥0.5 tokens after 0.15s at 600/min, got {available:.3f}"
        assert available <= 600.0, "tokens must never exceed max"
