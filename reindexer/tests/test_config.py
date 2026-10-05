"""
Unit tests for Config chunk-size validation.

Config fields resolve from environment variables at Config() construction time,
so each test builds its own instance after setting env vars rather than relying
on the pre-built app.config.config singleton.
"""
import pytest

from app.config import Config


class TestIdRangeChunkSize:

    def test_default_value(self, monkeypatch):
        monkeypatch.delenv("ID_RANGE_CHUNK_SIZE", raising=False)
        assert Config().id_range_chunk_size == 20000

    def test_overridable_via_env(self, monkeypatch):
        monkeypatch.setenv("ID_RANGE_CHUNK_SIZE", "50000")
        assert Config().id_range_chunk_size == 50000


@pytest.mark.parametrize("env_var", ["STREAM_CHUNK_SIZE", "ID_RANGE_CHUNK_SIZE"])
@pytest.mark.parametrize("value", ["0", "-1"])
def test_non_positive_chunk_size_rejected_at_startup(monkeypatch, env_var, value):
    """0 would make the paged scan fetch nothing and the id-range scan re-probe forever."""
    monkeypatch.setenv(env_var, value)
    with pytest.raises(ValueError, match=env_var):
        Config()
