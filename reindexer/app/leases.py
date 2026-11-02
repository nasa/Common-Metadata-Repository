"""Jobs whose heartbeat lease this task holds; renewed by app.lease_keeper.
No app imports, so any module can use it without an import cycle."""
import functools
import threading
from contextlib import contextmanager

_held: set[str] = set()
_lock = threading.Lock()


def hold(job_id: str) -> None:
    """Start holding the job's lease; it stays held until release()."""
    with _lock:
        _held.add(job_id)


def release(job_id: str) -> None:
    """Stop holding the job's lease, so the lease keeper no longer renews it."""
    with _lock:
        _held.discard(job_id)


@contextmanager
def held(job_id: str):
    """Context manager: hold the job's lease for the with-block, releasing it on exit, even
    if the block raises."""
    hold(job_id)
    try:
        yield
    finally:
        release(job_id)


def holds_lease(fn):
    """Decorator: hold the lease on fn's first argument (the job id) while fn runs, and
    release it when fn returns or raises."""
    @functools.wraps(fn)
    def wrapper(job_id, *args, **kwargs):
        with held(job_id):
            return fn(job_id, *args, **kwargs)
    return wrapper


def held_jobs() -> set[str]:
    """The job ids whose leases this task currently holds."""
    with _lock:
        return set(_held)
