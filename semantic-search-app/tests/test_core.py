import json
from types import SimpleNamespace

import pytest

from semantic_search.documents import build_passages, passage_id
from semantic_search.importer import (
    INDEX_MAPPING,
    ImportManager,
    ImportValidationError,
    validate_jsonl,
)
from semantic_search.models import Collection, ImportJob
from semantic_search.search import deduplicate, parse_bounding_box, parse_temporal, rrf


RECORD = {
    "schema_version": 1, "concept_id": "C1-P", "short_name": "SEA", "title": "Ocean data",
    "temporal": {"start": "2000-01-01T00:00:00Z", "end": "2025-01-01T00:00:00Z"},
    "spatial": {"type": "Polygon", "coordinates": [[[0, 0], [1, 0], [1, 1], [0, 0]]]},
    "variables": [{"concept_id": "V1-P", "name": "Sea temperature", "units": ["kelvin"]}],
}


def test_index_mapping_uses_query_time_boosts_only():
    properties = INDEX_MAPPING["mappings"]["properties"]
    assert all("boost" not in mapping for mapping in properties.values())


def test_passages_are_deterministic_and_include_variable_context():
    passages = build_passages(Collection.model_validate(RECORD))
    assert len(passages) == 2
    assert passages[0]["passage_id"] == passage_id("C1-P")
    assert passages[1]["passage_id"] == passage_id("C1-P", "V1-P")
    assert "Ocean data" in passages[1]["passage_text"]
    assert passages[1]["variable_id"] == "V1-P"


def test_validate_complete_file_before_processing(tmp_path):
    source = tmp_path / "source.jsonl"
    source.write_text(json.dumps(RECORD) + "\n{" + "\n", encoding="utf-8")
    with pytest.raises(ImportValidationError, match="line 2"):
        validate_jsonl(source)


def test_validate_rejects_duplicate_ids(tmp_path):
    source = tmp_path / "source.jsonl"
    source.write_text("\n".join((json.dumps(RECORD), json.dumps(RECORD))), encoding="utf-8")
    with pytest.raises(ImportValidationError, match="line 2: duplicate"):
        validate_jsonl(source)


async def test_importer_embeds_and_indexes_bounded_batches(monkeypatch):
    bulk_batches = []

    async def fake_bulk(es, actions, **options):
        documents = list(actions)
        bulk_batches.append(documents)
        assert options["chunk_size"] == 2
        return len(documents), []

    class Embedder:
        async def embed(self, text):
            return [float(len(text))], 3

    monkeypatch.setattr("semantic_search.importer.async_bulk", fake_bulk)
    manager = ImportManager(
        SimpleNamespace(embedding_concurrency=2, elasticsearch_batch_size=2),
        s3=None,
        embedder=Embedder(),
        es=object(),
    )
    job = ImportJob(import_id="import-1", status="running", source_uri="s3://bucket/key")
    passages = (
        {"passage_id": f"passage-{number}", "passage_text": f"text-{number}"}
        for number in range(5)
    )

    await manager._embed_and_index("target-index", passages, job)

    assert [len(batch) for batch in bulk_batches] == [2, 2, 1]
    assert all(
        document["_source"]["embedding"] == [6.0]
        for batch in bulk_batches
        for document in batch
    )
    assert job.bedrock_input_tokens == 15
    assert job.indexed_documents == 5
    assert job.failed_documents == 0


def _hit(collection, passage):
    return {"_source": {"collection_id": collection, "passage_text": passage}}


def test_deduplication_precedes_rrf():
    lexical = [_hit("C1", "a"), _hit("C1", "b"), _hit("C2", "c")]
    semantic = [_hit("C2", "c"), _hit("C1", "a")]
    assert [h["_source"]["collection_id"] for h in deduplicate(lexical)] == ["C1", "C2"]
    ordered, scores, _ = rrf(lexical, semantic)
    assert ordered == ["C1", "C2"]
    assert scores["C1"] == scores["C2"]


def test_filters():
    assert parse_temporal("2000-01-01T00:00:00Z,2001-01-01T00:00:00Z")
    assert parse_bounding_box("-10,-5,10,5")
    with pytest.raises(ValueError, match="antimeridian"):
        parse_bounding_box("170,-5,-170,5")
