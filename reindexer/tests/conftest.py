import os

import pytest

# Unit tests never touch a real Oracle instance.  Set the backend to the
# in-memory stub before any app modules are imported so that app.db does not
# try to instantiate OracleClient (which would call oracledb.create_pool).
os.environ.setdefault("DB_BACKEND", "stub")


@pytest.fixture(autouse=True)
def _reset_es_health_cache():
    """check_all_es_health caches its result for a few seconds (module-level state,
    shared process-wide). Without a reset, one test's cached value can leak into the
    next test that expects a different mocked httpx response — reset before every
    test regardless of whether it touches ES health directly."""
    import app.es.health as health_mod
    health_mod._cached_health = None
    health_mod._cached_at = 0.0
    yield
    health_mod._cached_health = None
    health_mod._cached_at = 0.0
