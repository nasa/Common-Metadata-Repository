import json
import tempfile

import pytest
from pydantic import ValidationError

from proxy.config import LaneConfig, LanesConfig, ProxySettings, load_lanes_config


class TestProxySettingsDefaults:
    def test_backend_url_required(self, monkeypatch):
        """backend_url has no default — must come from CMR_PROXY_BACKEND_URL."""
        monkeypatch.delenv("CMR_PROXY_BACKEND_URL")
        with pytest.raises(ValidationError, match="backend_url"):
            ProxySettings()

    def test_redis_url_required(self, monkeypatch):
        """redis_url has no default — must come from CMR_PROXY_REDIS_URL."""
        monkeypatch.delenv("CMR_PROXY_REDIS_URL")
        with pytest.raises(ValidationError, match="redis_url"):
            ProxySettings()

    def test_backend_url_from_env(self):
        """backend_url is read from the env var set by conftest."""
        s = ProxySettings()
        assert s.backend_url == "http://localhost:3003"

    def test_redis_url_from_env(self):
        """redis_url is read from the env var set by conftest."""
        s = ProxySettings()
        assert s.redis_url == "redis://localhost:6379"

    def test_max_request_body_bytes(self):
        s = ProxySettings()
        assert s.max_request_body_bytes == 52_428_800

    def test_backend_timeout_seconds(self):
        s = ProxySettings()
        assert s.backend_timeout_seconds == 300.0

    def test_redis_socket_connect_timeout(self):
        s = ProxySettings()
        assert s.redis_socket_connect_timeout == 2.0

    def test_redis_socket_timeout(self):
        s = ProxySettings()
        assert s.redis_socket_timeout == 2.0

    def test_redis_health_check_interval(self):
        s = ProxySettings()
        assert s.redis_health_check_interval == 30

    def test_backend_max_connections(self):
        s = ProxySettings()
        assert s.backend_max_connections == 500

    def test_backend_max_keepalive(self):
        s = ProxySettings()
        assert s.backend_max_keepalive == 200

    def test_lanes_config_default_path(self):
        s = ProxySettings()
        assert s.lanes_config == "lanes.json"


class TestEnvVarOverrides:
    def test_proxy_backend_url_override(self, monkeypatch):
        monkeypatch.setenv("CMR_PROXY_BACKEND_URL", "http://search:9999")
        s = ProxySettings()
        assert s.backend_url == "http://search:9999"

    def test_proxy_redis_url_override(self, monkeypatch):
        monkeypatch.setenv("CMR_PROXY_REDIS_URL", "redis://redis-cluster:6380")
        s = ProxySettings()
        assert s.redis_url == "redis://redis-cluster:6380"

    def test_proxy_backend_timeout_override(self, monkeypatch):
        monkeypatch.setenv("CMR_PROXY_BACKEND_TIMEOUT_SECONDS", "60.0")
        s = ProxySettings()
        assert s.backend_timeout_seconds == 60.0

    def test_proxy_lanes_config_override(self, monkeypatch):
        monkeypatch.setenv("CMR_PROXY_LANES_CONFIG", "/etc/cmr/lanes.json")
        s = ProxySettings()
        assert s.lanes_config == "/etc/cmr/lanes.json"


# Lane config model


class TestLaneConfigModel:
    def test_minimal_lane(self):
        lane = LaneConfig(name="test", permits=10)
        assert lane.name == "test"
        assert lane.permits == 10
        assert lane.overflow is None
        assert lane.retry_after == 5
        assert lane.default is False

    def test_full_lane(self):
        lane = LaneConfig(
            name="express",
            permits=200,
            overflow="standard",
            retry_after=3,
            default=True,
        )
        assert lane.overflow == "standard"
        assert lane.retry_after == 3
        assert lane.default is True


# Lanes config validation


class TestLanesConfigValidation:
    def test_valid_three_lane_config(self):
        config = LanesConfig(
            lanes=[
                LaneConfig(
                    name="express", permits=200, overflow="standard", default=True
                ),
                LaneConfig(name="standard", permits=150),
                LaneConfig(name="heavy", permits=50),
            ]
        )
        assert config.default_lane == "express"
        assert len(config.lanes) == 3

    def test_overflow_to_nonexistent_lane_fails(self):
        with pytest.raises(ValidationError, match="does not exist"):
            LanesConfig(
                lanes=[
                    LaneConfig(
                        name="fast", permits=10, overflow="missing", default=True
                    ),
                ]
            )

    def test_no_default_fails(self):
        with pytest.raises(ValidationError, match="default=true"):
            LanesConfig(
                lanes=[
                    LaneConfig(name="a", permits=10),
                    LaneConfig(name="b", permits=10),
                ]
            )

    def test_multiple_defaults_fails(self):
        with pytest.raises(ValidationError, match="default=true"):
            LanesConfig(
                lanes=[
                    LaneConfig(name="a", permits=10, default=True),
                    LaneConfig(name="b", permits=10, default=True),
                ]
            )

    def test_get_existing_lane(self):
        config = LanesConfig(
            lanes=[
                LaneConfig(name="fast", permits=10, default=True),
                LaneConfig(name="slow", permits=5),
            ]
        )
        lane = config.get("slow")
        assert lane.name == "slow"
        assert lane.permits == 5

    def test_get_unknown_lane_returns_default(self):
        config = LanesConfig(
            lanes=[
                LaneConfig(name="fast", permits=10, default=True),
                LaneConfig(name="slow", permits=5),
            ]
        )
        lane = config.get("nonexistent")
        assert lane.name == "fast"


# Loading from JSON file


class TestLoadLanesConfig:
    def test_loads_default_lanes_json(self):
        """The lanes.json in the project root should load successfully."""
        config = load_lanes_config("lanes.json")
        assert config.default_lane == "express"
        assert len(config.lanes) == 3

        # Verify the values match what's in the file
        express = config.get("express")
        assert express.permits == 200
        assert express.overflow == "standard"

        heavy = config.get("heavy")
        assert heavy.permits == 50
        assert heavy.retry_after == 10

    def test_loads_from_absolute_path(self):
        """Verify loading from an absolute path works."""
        lanes = [
            {"name": "only", "permits": 100, "default": True},
        ]
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as f:
            json.dump(lanes, f)
            f.flush()
            config = load_lanes_config(f.name)

        assert config.default_lane == "only"
        assert config.get("only").permits == 100

    def test_four_lane_custom_config(self):
        """Verify a custom 4-lane config loads and validates."""
        lanes = [
            {
                "name": "fast",
                "permits": 300,
                "overflow": "normal",
                "default": True,
            },
            {"name": "normal", "permits": 200},
            {"name": "slow", "permits": 100},
            {"name": "bulk", "permits": 20, "retry_after": 30},
        ]
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as f:
            json.dump(lanes, f)
            f.flush()
            config = load_lanes_config(f.name)

        assert len(config.lanes) == 4
        assert config.get("bulk").retry_after == 30
        assert config.get("slow").permits == 100
