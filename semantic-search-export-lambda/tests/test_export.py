import hashlib
from datetime import UTC, datetime

import pytest

from cmr_export.config import ConfigurationError, Settings, invocation
from cmr_export.exporter import fetch_collections, retry, run_export, write_jsonl
from cmr_export.handler import lambda_handler
from cmr_export.task import task_event, versioned_key
from cmr_export.transform import (
    RecordValidationError,
    collection,
    flatten_science_keywords,
    spatial,
    temporal,
    variable,
)


def source(cid="C1-P", variables=None):
    return {
        "concept-id": cid,
        "short-name": " SN ",
        "entry-title": "Title",
        "provider-id": "P",
        "science-keywords": [
            {"category": "EARTH SCIENCE", "topic": "OCEANS", "term": "TEMPERATURE"}
        ],
        "platform-sn": ["Terra", "Terra"],
        "instrument-sn": ["MODIS"],
        "temporals": [
            {"start-date": "2020-01-02T00:00:00Z", "end-date": "2021-01-01T01:00:00+01:00"},
            {"start-date": "2019-01-01T00:00:00Z", "end-date": "bad"},
        ],
        "mbr-west": -10,
        "mbr-south": -5,
        "mbr-east": 10,
        "mbr-north": 5,
        "mbr-crosses-antimeridian": False,
        "variable-concept-ids": variables or [],
    }


class ES:
    def __init__(self, collections, variables=(), fail_search=False):
        self.collections = collections
        self.variables = variables
        self.calls = 0
        self.closed = 0
        self.fail_search = fail_search

    def open_point_in_time(self, **_):
        return {"id": "pit"}

    def close_point_in_time(self, **_):
        self.closed += 1

    def search(self, **kwargs):
        if self.fail_search:
            raise RuntimeError("boom")
        if "pit" in kwargs:
            start = self.calls * kwargs["size"]
            self.calls += 1
            docs = self.collections[start : start + kwargs["size"]]
            return {
                "hits": {
                    "hits": [
                        {"_source": d, "sort": [d["concept-id"], start + i]}
                        for i, d in enumerate(docs)
                    ]
                }
            }
        wanted = set(kwargs["query"]["bool"]["filter"][0]["terms"]["concept-id"])
        return {
            "hits": {"hits": [{"_source": d} for d in self.variables if d["concept-id"] in wanted]}
        }


class S3:
    def __init__(self, fail_copy=False):
        self.calls = []
        self.fail_copy = fail_copy

    def upload_file(self, *args, **kwargs):
        self.calls.append(("upload", args, kwargs))

    def copy_object(self, **kwargs):
        self.calls.append(("copy", kwargs))
        if self.fail_copy:
            raise RuntimeError("copy failed")

    def delete_object(self, **kwargs):
        self.calls.append(("delete", kwargs))


SETTINGS = Settings("http://example", page_size=1, variable_batch_size=2)


def test_transform_complete_and_optional_fields():
    record, warning = collection(
        source(variables=["V1-P"]),
        [
            variable(
                {
                    "concept-id": "V1-P",
                    "variable-name": "Temperature",
                    "measurement": "Kelvin",
                    "definition": " Surface temperature ",
                }
            )
        ],
    )
    assert warning is None and record["short_name"] == "SN"
    assert record["science_keywords"] == ["EARTH SCIENCE > OCEANS > TEMPERATURE"]
    assert record["temporal"] == {"start": "2020-01-02T00:00:00Z", "end": "2021-01-01T00:00:00Z"}
    assert record["spatial"]["coordinates"][0][0] == [-10.0, -5.0]
    assert record["spatial"]["coordinates"][0][0] == record["spatial"]["coordinates"][0][-1]
    assert record["variables"][0]["measurements"] == ["Kelvin"]


def test_validation_and_helpers():
    with pytest.raises(RecordValidationError):
        collection({}, [])
    with pytest.raises(RecordValidationError):
        variable({"concept-id": "V1"})
    assert flatten_science_keywords({"science-keywords-flat": ["B", "A", "A"]}) == ["A", "B"]
    assert (
        temporal({"temporals": [{"start-date": "2020-01-01", "end-date": "2021-01-01Z"}]}) is None
    )
    assert spatial({"mbr-crosses-antimeridian": True}) == (None, "antimeridian")
    assert spatial({"mbr-west": 2, "mbr-east": 1, "mbr-south": 0, "mbr-north": 1})[0] is None


def test_pagination_limit_provider_query_and_cleanup():
    es = ES([source("C1-P"), source("C2-P"), source("C3-P")])
    records, more = fetch_collections(es, "alias", ["P"], 2, 1)
    assert [r["concept-id"] for r in records] == ["C1-P", "C2-P"]
    assert more is True and es.closed == 1
    broken = ES([], fail_search=True)
    with pytest.raises(RuntimeError):
        fetch_collections(broken, "alias", [], 2, 1)
    assert broken.closed == 1


def test_retry_transient_only():
    class Error(Exception):
        def __init__(self, status):
            self.status_code = status

    calls = []

    def transient():
        calls.append(1)
        if len(calls) < 3:
            raise Error(503)
        return "ok"

    assert retry(transient, sleeper=lambda _: None, jitter=lambda: 0) == "ok"
    with pytest.raises(Error):
        retry(lambda: (_ for _ in ()).throw(Error(401)), sleeper=lambda _: None)


def test_jsonl_and_successful_staging(tmp_path):
    path = tmp_path / "x.jsonl"
    records = [{"schema_version": 1, "concept_id": "C1"}]
    size, digest, count = write_jsonl(records, path)
    assert path.read_bytes().endswith(b"\n") and not path.read_bytes().startswith(b"[")
    assert (
        size == len(path.read_bytes()) and digest == hashlib.sha256(path.read_bytes()).hexdigest()
    )
    assert count == 0
    es = ES(
        [source(variables=["V1-P", "V1-P"]), source("C2-P", ["V1-P"])],
        [{"concept-id": "V1-P", "variable-name": "Var", "measurement": "M"}],
    )
    s3 = S3()
    result = run_export(es, s3, SETTINGS, "bucket", "new/key.jsonl", [], 2)
    assert result["collections"] == 2 and result["variables"] == 1
    assert result["warnings"] == {"duplicate_variable_association": 1}
    assert [call[0] for call in s3.calls] == ["upload", "copy", "delete"]
    assert s3.calls[0][2]["ExtraArgs"]["ContentType"] == "application/x-ndjson"


def test_staging_cleanup_on_failure():
    s3 = S3(fail_copy=True)
    with pytest.raises(RuntimeError):
        run_export(ES([source()]), s3, SETTINGS, "b", "k", [], 1)
    assert [call[0] for call in s3.calls][-1] == "delete"


def test_event_validation_and_handler_injection():
    assert invocation(
        {"bucket": "b", "key": "k", "provider_ids": ["P", "P"], "max_collections": 2}, SETTINGS
    ) == ("b", "k", ["P"], 2)
    for bad in [0, -1, True, "1.0", 1001]:
        with pytest.raises(ConfigurationError):
            invocation({"bucket": "b", "key": "k", "max_collections": bad}, SETTINGS)
    result = lambda_handler(
        {"bucket": "b", "key": "k", "max_collections": 1},
        None,
        es=ES([source()]),
        s3=S3(),
        settings=SETTINGS,
    )
    assert result["collections"] == 1


def test_task_uses_unique_versioned_key_under_configured_prefix():
    key = versioned_key(
        "/semantic-collections/",
        now=datetime(2026, 8, 23, 12, 34, 56, tzinfo=UTC),
        identifier="abc123",
    )
    assert key == "semantic-collections/collections-20260823T123456Z-abc123.jsonl"
    settings = Settings("http://example", bucket="bucket", s3_prefix="semantic-collections")
    assert task_event(settings)["key"].startswith("semantic-collections/collections-")


def test_task_allows_explicit_key_override():
    settings = Settings("http://example", bucket="bucket", key="exports/manual.jsonl")
    assert task_event(settings) == {"bucket": "bucket", "key": "exports/manual.jsonl"}
