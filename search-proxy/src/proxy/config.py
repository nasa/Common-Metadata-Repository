import json
from pathlib import Path
from typing import List

from pydantic import BaseModel, field_validator, model_validator
from pydantic_settings import BaseSettings


class ProxySettings(BaseSettings):
    backend_url: str
    redis_url: str
    max_request_body_bytes: int = 52_428_800
    max_cache_response_bytes: int = 1_048_576
    backend_timeout_seconds: float = 300.0
    redis_socket_connect_timeout: float = 2.0
    redis_socket_timeout: float = 2.0
    redis_health_check_interval: int = 30
    redis_max_connections: int | None = None
    backend_max_connections: int = 500
    backend_max_keepalive: int = 200

    lanes_config: str = "lanes.json"
    lanes_json: str | None = None

    log_level: str = "INFO"
    bypass_enabled: bool = False
    cache_enabled: bool = True
    load_shedding_enabled: bool = True
    classification_enabled: bool = True

    model_config = {"env_prefix": "CMR_PROXY_"}


class LaneConfig(BaseModel):
    """Configuration for a single traffic lane. Defined in lanes.json."""

    name: str
    permits: int
    overflow: str | None = None
    cache_ttl: int = 0
    retry_after: int = 5
    default: bool = False

    @field_validator("name")
    @classmethod
    def name_must_not_be_blank(cls, v):
        if not v.strip():
            raise ValueError("lane name must not be blank or whitespace-only")
        return v

    @field_validator("permits")
    @classmethod
    def permits_must_be_positive(cls, v):
        if v < 1:
            raise ValueError(f"permits must be at least 1, got {v}")
        return v

    @field_validator("cache_ttl")
    @classmethod
    def cache_ttl_must_be_non_negative(cls, v):
        if v < 0:
            raise ValueError(f"cache_ttl must be >= 0, got {v}")
        return v

    @field_validator("retry_after")
    @classmethod
    def retry_after_must_be_positive(cls, v):
        if v < 1:
            raise ValueError(f"retry_after must be at least 1, got {v}")
        return v


class LanesConfig(BaseModel):
    """Validated collection of lane definitions loaded from lanes.json."""

    lanes: List[LaneConfig]

    @model_validator(mode="after")
    def validate_lanes(self):
        all_names = [lane.name for lane in self.lanes]
        names = set(all_names)

        # Lane names must be unique
        if len(all_names) != len(names):
            seen, dupes = set(), []
            for n in all_names:
                if n in seen:
                    dupes.append(n)
                seen.add(n)
            raise ValueError(f"Duplicate lane names: {sorted(dupes)}")

        # Every overflow target must reference an existing lane
        for lane in self.lanes:
            if lane.overflow and lane.overflow not in names:
                raise ValueError(
                    f"Lane '{lane.name}' overflows to '{lane.overflow}' "
                    f"which does not exist. Available: {sorted(names)}"
                )

        # Self-overflow and cycle detection
        for lane in self.lanes:
            if lane.overflow == lane.name:
                raise ValueError(f"Lane '{lane.name}' overflows to itself")

        overflow_map = {lane.name: lane.overflow for lane in self.lanes}
        for start in overflow_map:
            visited, current = {start}, overflow_map.get(start)
            while current:
                if current in visited:
                    raise ValueError(
                        f"Overflow cycle detected involving lane '{current}'"
                    )
                visited.add(current)
                current = overflow_map.get(current)

        # Exactly one lane must be marked as default
        defaults = [lane for lane in self.lanes if lane.default]
        if len(defaults) != 1:
            raise ValueError(
                f"Exactly one lane must have default=true, found {len(defaults)}"
            )

        return self

    @property
    def default_lane(self) -> str:
        """The name of the default lane."""
        return next(lane.name for lane in self.lanes if lane.default)

    def get(self, name: str) -> LaneConfig:
        """Look up a lane by name. Returns the default lane if unknown."""
        for lane in self.lanes:
            if lane.name == name:
                return lane
        return next(lane for lane in self.lanes if lane.default)


def parse_lanes_config(json_str: str) -> LanesConfig:
    """Parse and validate lanes config from a JSON string."""
    return LanesConfig(lanes=json.loads(json_str))


def load_lanes_config(path: str = "lanes.json") -> LanesConfig:
    """Load and validate lane definitions from a JSON file."""
    config_path = Path(path)
    if not config_path.is_absolute():
        config_path = Path(__file__).parent.parent.parent / config_path

    with open(config_path) as f:
        raw = json.load(f)

    return LanesConfig(lanes=raw)
