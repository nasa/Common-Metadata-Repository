import hashlib
import json
from typing import Optional

import redis.asyncio


class ResponseCache:
    """Redis-backed response cache keyed on the full request signature."""

    def __init__(
        self,
        redis_client: redis.asyncio.Redis,
        max_response_bytes: int,
    ):
        self.redis = redis_client
        self.max_response_bytes = max_response_bytes

    def _build_key(self, method: str, path: str, query: str, auth_token: str) -> str:
        """Hash the full request signature into a Redis key."""
        raw = f"{method}|{path}|{query}|{auth_token}"
        digest = hashlib.sha256(raw.encode()).hexdigest()
        return f"cache:{digest}"

    async def get(
        self, method: str, path: str, query: str, auth_token: str
    ) -> Optional[dict]:
        """Look up a cached response. Returns None on miss."""
        key = self._build_key(method, path, query, auth_token)
        cached = await self.redis.get(key)
        if cached:
            return json.loads(cached)
        return None

    async def set(
        self,
        method: str,
        path: str,
        query: str,
        auth_token: str,
        response_data: dict,
        response_size: int,
        ttl: int,
    ):
        """Store a response with the given TTL. Skips oversized responses."""
        if response_size > self.max_response_bytes:
            return

        key = self._build_key(method, path, query, auth_token)
        await self.redis.setex(key, ttl, json.dumps(response_data))
