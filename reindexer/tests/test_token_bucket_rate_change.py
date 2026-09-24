"""Deterministic coverage for token accounting across live rate changes."""

from types import SimpleNamespace

from fastapi import FastAPI
from fastapi.testclient import TestClient
import pytest

from app.auth import require_auth
from app.routers import throttle
from app.throttler import token_bucket
from app.throttler.token_bucket import TokenBucket
from app.throttler.worker import ThrottlerWorker


@pytest.fixture
def clock(monkeypatch):
    """Replace only the bucket's clock, without changing other threads' time."""
    now = [100.0]

    def unexpected_sleep(_seconds):
        pytest.fail("The deterministic test should never wait for tokens")

    monkeypatch.setattr(
        token_bucket,
        "time",
        SimpleNamespace(
            monotonic=lambda: now[0],
            sleep=unexpected_sleep,
        ),
    )
    return now


@pytest.mark.parametrize("method", ["update_rate", "set_rate"])
@pytest.mark.parametrize(
    "old_rate,new_rate,remaining,elapsed",
    [
        (60, 600, 0, 30),
        (600, 60, 40, 1),
        (60, 600, 60, 600),
        (600, 60, 0, 30),
        (60, 60, 20, 5),
        (60, 600, 20, 0),
    ],
)
def test_elapsed_tokens_use_previous_rate(
    clock, method, old_rate, new_rate, remaining, elapsed
):
    """Refill up to the old capacity before imposing the new capacity/rate."""
    bucket = TokenBucket(old_rate)
    if old_rate > remaining:
        assert bucket.consume(old_rate - remaining)
    clock[0] += elapsed
    getattr(bucket, method)(new_rate)

    expected = min(new_rate, min(old_rate, remaining + elapsed * old_rate / 60))
    assert bucket.tokens_available == pytest.approx(expected)
    assert bucket.current_rate == pytest.approx(new_rate)

    # Tokens after the update must accrue using the new rate.
    assert bucket.consume(1)
    clock[0] += 2
    assert bucket.tokens_available == pytest.approx(
        min(new_rate, expected - 1 + 2 * new_rate / 60)
    )


def test_successive_updates_preserve_each_elapsed_interval(clock):
    """Account separately for intervals that have no intervening reads."""
    bucket = TokenBucket(60)
    assert bucket.consume(60)
    clock[0] += 10
    bucket.update_rate(120)
    clock[0] += 5
    bucket.update_rate(300)
    clock[0] += 3
    assert bucket.tokens_available == pytest.approx(35)


def test_live_throttle_endpoint_preserves_accrued_tokens(clock, monkeypatch):
    """Exercise the actual endpoint and worker with all services left stopped."""
    worker = ThrottlerWorker()
    worker.set_rate(60)
    assert worker._token_bucket.consume(60)
    clock[0] += 30
    monkeypatch.setattr(throttle, "throttler", worker)
    app = FastAPI()
    app.include_router(throttle.router, prefix="/reindexer")
    app.dependency_overrides[require_auth] = lambda: "test-token"
    with TestClient(app) as client:
        response = client.put("/reindexer/throttle", json={"rate_per_minute": 600})
        assert response.status_code == 200
        assert response.json() == {"rate_per_minute": 600}
        assert client.get("/reindexer/throttle").json() == {"rate_per_minute": 600}
    assert worker.token_state()["tokens_available"] == pytest.approx(30)
    assert worker.liveness()["alive"] is False
