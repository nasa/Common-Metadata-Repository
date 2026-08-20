from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    aws_region: str = "us-east-1"
    elasticsearch_url: str
    elasticsearch_api_key: str | None = None
    elasticsearch_username: str | None = None
    elasticsearch_password: str | None = None
    bedrock_model_id: str = "amazon.titan-embed-text-v2:0"
    semantic_index_alias: str = "semantic_collections"
    embedding_concurrency: int = Field(4, ge=1, le=32)
    elasticsearch_batch_size: int = Field(100, ge=1, le=1000)
    bedrock_max_attempts: int = Field(5, ge=1, le=10)
    import_control_index: str = "semantic_search_imports"


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]

