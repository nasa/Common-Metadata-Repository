import os

import pytest

# Before any app import, so app.db never creates a real Oracle pool.
os.environ.setdefault("DB_BACKEND", "stub")


@pytest.fixture(autouse=True)
def _reset_es_health_cache():
    """So one test's mocked ES health can't leak into the next through the cache."""
    import app.es.health as health_mod
    health_mod._cached_health = None
    health_mod._cached_at = 0.0
    yield
    health_mod._cached_health = None
    health_mod._cached_at = 0.0
