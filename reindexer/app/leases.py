"""Jobs whose heartbeat lease this task holds; renewed by app.lease_keeper.
No app imports, so any module can use it without an import cycle."""
import functools
import threading
from contextlib import contextmanager

_held: set[str] = set()
_lock = threading.Lock()


def hold(job_id: str) -> None:
    with _lock:
        _held.add(job_id)


def release(job_id: str) -> None:
    with _lock:
        _held.discard(job_id)


@contextmanager
def held(job_id: str):
    hold(job_id)
    try:
        yield
    finally:
        release(job_id)


def holding(fn):
    """Decorator: hold the lease on fn's first argument (the job id) while it runs."""
    @functools.wraps(fn)
    def wrapper(job_id, *args, **kwargs):
        with held(job_id):
            return fn(job_id, *args, **kwargs)
    return wrapper


def is_heartbeat_leased(job: dict) -> bool:
    """Jobs leased by last_heartbeat (the rest are collection-queue work, leased by SQS).
    Must match JobStore.find_lapsed_jobs."""
    return job.get("status") == "running" or (
        job.get("status") == "dispatching" and job.get("concept_type") == "granules-by-provider"
    )


def held_jobs() -> set[str]:
    with _lock:
        return set(_held)
