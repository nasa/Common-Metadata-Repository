import json
from pathlib import Path
from typing import List, Optional

from pydantic import BaseModel, model_validator
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
    backend_max_connections: int = 500
    backend_max_keepalive: int = 200

    lanes_config: str = "lanes.json"

    model_config = {"env_prefix": "CMR_PROXY_"}


class LaneConfig(BaseModel):
    """Configuration for a single traffic lane. Defined in lanes.json."""

    name: str
    permits: int
    overflow: Optional[str] = None
    cache_ttl: int = 0
    retry_after: int = 5
    default: bool = False


class LanesConfig(BaseModel):
    """Validated collection of lane definitions loaded from lanes.json."""

    lanes: List[LaneConfig]

    @model_validator(mode="after")
    def validate_lanes(self):
        names = {lane.name for lane in self.lanes}

        # Every overflow target must reference an existing lane
        for lane in self.lanes:
            if lane.overflow and lane.overflow not in names:
                raise ValueError(
                    f"Lane '{lane.name}' overflows to '{lane.overflow}' "
                    f"which does not exist. Available: {sorted(names)}"
                )

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
        return self.get(self.default_lane)


def load_lanes_config(path: str = "lanes.json") -> LanesConfig:
    """Load and validate lane definitions from a JSON file."""
    config_path = Path(path)
    if not config_path.is_absolute():
        config_path = Path(__file__).parent.parent.parent / config_path

    with open(config_path) as f:
        raw = json.load(f)

    return LanesConfig(lanes=raw)
