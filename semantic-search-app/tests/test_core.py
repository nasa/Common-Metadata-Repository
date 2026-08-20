import json

import pytest

from semantic_search.documents import build_passages, passage_id
from semantic_search.importer import ImportValidationError, validate_jsonl
from semantic_search.models import Collection
from semantic_search.search import deduplicate, parse_bounding_box, parse_temporal, rrf


RECORD = {
    "schema_version": 1, "concept_id": "C1-P", "short_name": "SEA", "title": "Ocean data",
    "temporal": {"start": "2000-01-01T00:00:00Z", "end": "2025-01-01T00:00:00Z"},
    "spatial": {"type": "Polygon", "coordinates": [[[0, 0], [1, 0], [1, 1], [0, 0]]]},
    "variables": [{"concept_id": "V1-P", "name": "Sea temperature", "units": ["kelvin"]}],
}


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
