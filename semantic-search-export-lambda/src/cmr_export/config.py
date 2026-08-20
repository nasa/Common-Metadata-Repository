import os
from dataclasses import dataclass


class ConfigurationError(ValueError):
    pass


def _positive_int(name: str, value: object) -> int:
    if isinstance(value, bool):
        raise ConfigurationError(f"{name} must be a positive integer")
    try:
        parsed = int(value)
    except (TypeError, ValueError) as error:
        raise ConfigurationError(f"{name} must be a positive integer") from error
    if parsed <= 0 or str(value).strip() != str(parsed):
        raise ConfigurationError(f"{name} must be a positive integer")
    return parsed


@dataclass(frozen=True)
class Settings:
    elasticsearch_url: str
    collection_alias: str = "collection_search_alias"
    variable_alias: str = "variables"
    bucket: str | None = None
    key: str | None = None
    page_size: int = 100
    variable_batch_size: int = 500
    max_collections: int = 1000
    verify_certs: bool = True
    api_key: str | None = None
    username: str | None = None
    password: str | None = None
    sse: str | None = None
    kms_key_id: str | None = None

    @classmethod
    def from_env(cls, env=os.environ):
        url = env.get("ELASTICSEARCH_URL", "").strip()
        if not url:
            raise ConfigurationError("ELASTICSEARCH_URL is required")
        return cls(
            elasticsearch_url=url,
            collection_alias=env.get("COLLECTION_ALIAS", "collection_search_alias"),
            variable_alias=env.get("VARIABLE_ALIAS", "variables"),
            bucket=env.get("S3_BUCKET"),
            key=env.get("S3_KEY"),
            page_size=_positive_int("PAGE_SIZE", env.get("PAGE_SIZE", "100")),
            variable_batch_size=_positive_int(
                "VARIABLE_BATCH_SIZE", env.get("VARIABLE_BATCH_SIZE", "500")
            ),
            max_collections=_positive_int("MAX_COLLECTIONS", env.get("MAX_COLLECTIONS", "1000")),
            verify_certs=env.get("ELASTICSEARCH_VERIFY_CERTS", "true").lower() == "true",
            api_key=env.get("ELASTICSEARCH_API_KEY"),
            username=env.get("ELASTICSEARCH_USERNAME"),
            password=env.get("ELASTICSEARCH_PASSWORD"),
            sse=env.get("S3_SERVER_SIDE_ENCRYPTION"),
            kms_key_id=env.get("S3_KMS_KEY_ID"),
        )


def invocation(event: dict, settings: Settings) -> tuple[str, str, list[str], int]:
    if not isinstance(event, dict):
        raise ConfigurationError("event must be an object")
    bucket = event.get("bucket", settings.bucket)
    key = event.get("key", settings.key)
    if not isinstance(bucket, str) or not bucket.strip():
        raise ConfigurationError("bucket is required")
    if not isinstance(key, str) or not key.strip() or key.startswith("/"):
        raise ConfigurationError("key must be a nonblank relative S3 object key")
    providers = event.get("provider_ids", [])
    if not isinstance(providers, list) or any(
        not isinstance(item, str) or not item.strip() for item in providers
    ):
        raise ConfigurationError("provider_ids must be a list of nonblank strings")
    providers = sorted(set(item.strip() for item in providers))
    limit = _positive_int("max_collections", event.get("max_collections", settings.max_collections))
    if limit > settings.max_collections:
        raise ConfigurationError("max_collections exceeds configured MAX_COLLECTIONS")
    return bucket.strip(), key.strip(), providers, limit
