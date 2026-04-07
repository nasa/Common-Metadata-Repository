import pytest


@pytest.fixture(autouse=True)
def proxy_env_vars(monkeypatch):
    """Set required env vars for ProxySettings in all tests.

    These have no defaults — the deployment must provide them. Tests set
    them here so ProxySettings can be instantiated without hitting
    validation errors."""
    monkeypatch.setenv("CMR_PROXY_BACKEND_URL", "http://localhost:3003")
    monkeypatch.setenv("CMR_PROXY_REDIS_URL", "redis://localhost:6379")
