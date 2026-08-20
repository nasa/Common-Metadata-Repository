from datetime import datetime
from typing import Annotated, Literal

from pydantic import BaseModel, Field, field_validator, model_validator


class Temporal(BaseModel):
    start: datetime
    end: datetime

    @model_validator(mode="after")
    def ordered(self):
        if self.start > self.end:
            raise ValueError("temporal start must not be after end")
        return self


class Spatial(BaseModel):
    type: Literal["Point", "MultiPoint", "LineString", "MultiLineString", "Polygon", "MultiPolygon"]
    coordinates: list

    @field_validator("coordinates")
    @classmethod
    def nonempty(cls, value):
        if not value:
            raise ValueError("coordinates must not be empty")
        return value


class Variable(BaseModel):
    concept_id: Annotated[str, Field(min_length=1)]
    name: Annotated[str, Field(min_length=1)]
    long_name: str | None = None
    definition: str | None = None
    measurements: list[str] = Field(default_factory=list)
    units: list[str] = Field(default_factory=list)


class Collection(BaseModel):
    schema_version: Literal[1]
    concept_id: Annotated[str, Field(min_length=1)]
    short_name: Annotated[str, Field(min_length=1)]
    title: Annotated[str, Field(min_length=1)]
    summary: str | None = None
    science_keywords: list[str] = Field(default_factory=list)
    platforms: list[str] = Field(default_factory=list)
    instruments: list[str] = Field(default_factory=list)
    temporal: Temporal | None = None
    spatial: Spatial | None = None
    variables: list[Variable] = Field(default_factory=list)


class ImportRequest(BaseModel):
    s3_uri: Annotated[str, Field(pattern=r"^s3://[^/]+/.+")]


class ImportJob(BaseModel):
    import_id: str
    status: Literal["pending", "running", "succeeded", "failed"]
    source_uri: str
    started_at: datetime | None = None
    completed_at: datetime | None = None
    collections_processed: int = 0
    passages_processed: int = 0
    bedrock_input_tokens: int = 0
    indexed_documents: int = 0
    failed_documents: int = 0
    failure_message: str | None = None

