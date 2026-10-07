"""Config validation."""
import pytest

from app.config import Config


@pytest.mark.parametrize("env_var", ["STREAM_CHUNK_SIZE", "ID_RANGE_CHUNK_SIZE", "LEASE_MINUTES"])
def test_non_positive_setting_rejected_at_startup(monkeypatch, env_var):
    """0 would make a collection job fetch nothing, a provider job re-probe the same id
    forever, and every lease lapse at once."""
    monkeypatch.setenv(env_var, "0")
    with pytest.raises(ValueError, match=env_var):
        Config()
