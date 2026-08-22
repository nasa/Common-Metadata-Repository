import asyncio
import json
import logging
import tempfile
from datetime import UTC, datetime
from pathlib import Path
from urllib.parse import urlparse
from uuid import uuid4

from elasticsearch.helpers import async_bulk
from pydantic import ValidationError

from .documents import build_passages
from .models import Collection, ImportJob

LOG = logging.getLogger(__name__)


class ImportValidationError(ValueError):
    pass


def validate_jsonl(path: Path) -> list[Collection]:
    collections, ids = [], set()
    with path.open(encoding="utf-8") as source:
        for line_number, line in enumerate(source, 1):
            try:
                value = json.loads(line)
                collection = Collection.model_validate(value)
            except (json.JSONDecodeError, ValidationError) as error:
                raise ImportValidationError(f"line {line_number}: {error}") from error
            all_ids = [collection.concept_id, *(v.concept_id for v in collection.variables)]
            duplicate = next((item for item in all_ids if item in ids), None)
            if duplicate or len(set(all_ids)) != len(all_ids):
                raise ImportValidationError(f"line {line_number}: duplicate concept ID {duplicate or 'within record'}")
            ids.update(all_ids)
            collections.append(collection)
    if not collections:
        raise ImportValidationError("JSONL file is empty")
    return collections


INDEX_MAPPING = {"mappings": {"properties": {
    "passage_id": {"type": "keyword"}, "passage_type": {"type": "keyword"},
    "collection_id": {"type": "keyword"}, "short_name": {"type": "text"},
    "title": {"type": "text"}, "summary": {"type": "text"},
    "science_keywords": {"type": "text"}, "platforms": {"type": "text"},
    "instruments": {"type": "text"}, "variable_id": {"type": "keyword"},
    "variable_name": {"type": "text"}, "variable_long_name": {"type": "text"},
    "variable_definition": {"type": "text"}, "measurements": {"type": "text"},
    "units": {"type": "text"}, "passage_text": {"type": "text"},
    "temporal": {"type": "date_range"}, "spatial": {"type": "geo_shape"},
    "embedding": {"type": "dense_vector", "dims": 1024, "index": True, "similarity": "cosine"},
}}}


class ImportManager:
    def __init__(self, settings, s3, embedder, es):
        self.settings, self.s3, self.embedder, self.es = settings, s3, embedder, es
        self.lock = asyncio.Lock()

    async def initialize(self):
        await self.es.options(ignore_status=400).indices.create(
            index=self.settings.import_control_index)
        await self.es.update_by_query(index=self.settings.import_control_index,
            query={"term": {"status.keyword": "running"}},
            script={"source": "ctx._source.status='failed'; ctx._source.failure_message='service restarted'; ctx._source.completed_at=params.now", "params": {"now": datetime.now(UTC).isoformat()}},
            conflicts="proceed", refresh=True)

    async def submit(self, source_uri: str) -> ImportJob:
        if self.lock.locked():
            raise RuntimeError("an import is already in progress")
        # Reserve the single import slot before yielding so two submissions cannot race.
        await self.lock.acquire()
        job = ImportJob(import_id=uuid4().hex, status="pending", source_uri=source_uri)
        try:
            await self._save(job)
            asyncio.create_task(self._run(job))
            return job
        except Exception:
            self.lock.release()
            raise

    async def get(self, import_id: str) -> ImportJob | None:
        try:
            response = await self.es.get(index=self.settings.import_control_index, id=import_id)
            return ImportJob.model_validate(response["_source"])
        except Exception as error:
            if getattr(error, "status_code", None) == 404:
                return None
            raise

    async def _save(self, job):
        await self.es.index(index=self.settings.import_control_index, id=job.import_id,
                            document=job.model_dump(mode="json"), refresh=True)

    async def _run(self, job):
        try:
            job.status, job.started_at = "running", datetime.now(UTC)
            await self._save(job)
            index = f"{self.settings.semantic_index_alias}_{job.import_id}"
            try:
                parsed = urlparse(job.source_uri)
                with tempfile.TemporaryDirectory() as directory:
                    path = Path(directory) / "source.jsonl"
                    await asyncio.to_thread(self.s3.download_file, parsed.netloc, parsed.path.lstrip("/"), str(path))
                    collections = validate_jsonl(path)
                passages = [p for collection in collections for p in build_passages(collection)]
                job.collections_processed, job.passages_processed = len(collections), len(passages)
                await self.es.indices.create(index=index, **INDEX_MAPPING)
                semaphore = asyncio.Semaphore(self.settings.embedding_concurrency)
                async def enrich(passage):
                    async with semaphore:
                        vector, tokens = await self.embedder.embed(passage["passage_text"])
                    job.bedrock_input_tokens += tokens
                    return {**passage, "embedding": vector}
                documents = await asyncio.gather(*(enrich(p) for p in passages))
                actions = ({"_index": index, "_id": p["passage_id"], "_source": p} for p in documents)
                success, failures = await async_bulk(self.es, actions,
                    chunk_size=self.settings.elasticsearch_batch_size, raise_on_error=False)
                job.indexed_documents, job.failed_documents = success, len(failures)
                if failures:
                    raise RuntimeError(f"{len(failures)} documents failed indexing")
                await self.es.indices.refresh(index=index)
                await self.es.search(index=index, size=1, query={"match_all": {}})
                await self.es.indices.update_aliases(actions=[
                    {"remove": {"index": "*", "alias": self.settings.semantic_index_alias, "must_exist": False}},
                    {"add": {"index": index, "alias": self.settings.semantic_index_alias}},
                ])
                job.status = "succeeded"
            except Exception as error:
                LOG.exception("import_failed", extra={"import_id": job.import_id})
                job.status, job.failure_message = "failed", str(error)[:500]
            finally:
                job.completed_at = datetime.now(UTC)
                await self._save(job)
        finally:
            self.lock.release()
