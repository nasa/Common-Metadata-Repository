import importlib
import io
import json
import os
import sys
import tempfile
import types
import unittest
from unittest import mock


# --- Ensure required env vars exist before importing module (module sys.exit(1) otherwise) ---
os.environ.setdefault("PROVIDER", "PROV1")
os.environ.setdefault("ENVIRONMENT", "test")

# --- Make top-level ./src importable ---
TOP_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(TOP_DIR, "src")
if SRC_DIR not in sys.path:
    sys.path.insert(0, SRC_DIR)

# --- If oracledb isn't installed in the unit-test environment, stub it ---
if "oracledb" not in sys.modules:
    sys.modules["oracledb"] = types.SimpleNamespace(Error=Exception)


class DummyResponse:
    def __init__(self, status_code=200, json_data=None, text=""):
        self.status_code = status_code
        self._json_data = json_data if json_data is not None else {}
        self.text = text

    def json(self):
        return self._json_data


class DummyCursorBatch:
    """Cursor for db_batch_read() tests, supports context manager + fetchmany batching."""
    def __init__(self, batches):
        self._batches = list(batches)
        self.executed = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, query):
        self.executed.append(query)

    def fetchmany(self, size):
        if not self._batches:
            return []
        return self._batches.pop(0)


class DummyCursorFetchone:
    """Cursor for fetchone() tests."""
    def __init__(self, fetchone_value):
        self._fetchone_value = fetchone_value
        self.executed = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, query, **params):
        self.executed.append((query, params))

    def fetchone(self):
        return self._fetchone_value


class DummyDBConnection:
    def __init__(self, cursor):
        self._cursor = cursor
        self.closed = False

    def cursor(self):
        return self._cursor

    def close(self):
        self.closed = True


def import_find_granules_with_patched_boto3():
    """
    Import find_granules while patching boto3.client so module-level SQS initialization
    doesn't call AWS.
    """
    if "find_granules" in sys.modules:
        del sys.modules["find_granules"]

    sqs_mock = mock.Mock()
    s3_mock = mock.Mock()

    def fake_boto3_client(service_name, *args, **kwargs):
        if service_name == "sqs":
            return sqs_mock
        if service_name == "s3":
            return s3_mock
        return mock.Mock()

    with mock.patch("boto3.client", side_effect=fake_boto3_client):
        mod = importlib.import_module("find_granules")
    return mod, sqs_mock, s3_mock


find_granules, SQS_MOCK, _S3_MOCK = import_find_granules_with_patched_boto3()


class FindGranulesTests(unittest.TestCase):
    def setUp(self):
        # Make CONFIG deterministic for tests
        self.config_patcher = mock.patch.object(
            find_granules,
            "CONFIG",
            {
                "BATCH_SIZE": 2,
                "GRAN_ELASTIC_URL": "http://example-gran-es",
                "ELASTIC_URL": "http://example-es",
                "EFS_PATH": "/efs/",
                "SQS_QUEUE_URL": "http://example-sqs-queue-url",
            },
        )
        self.config_patcher.start()

    def tearDown(self):
        self.config_patcher.stop()

    def test_elastic_search_query_builds_should_terms(self):
        db_batch = [
            ("G1", 5, "2020-01-01"),
            ("G2", 6, "2020-01-02"),
        ]
        q = find_granules.elastic_search_query(db_batch, query_size=0)

        self.assertEqual(q["_source"], ["concept-id", "revision-id", "revision-date"])
        self.assertEqual(q["size"], 0)
        should = q["query"]["bool"]["should"]
        self.assertEqual(len(should), 2)
        self.assertEqual(
            should[0]["bool"]["must"],
            [{"term": {"concept-id": "G1"}}, {"term": {"revision-id": "5"}}],
        )

    def test_report_file_name(self):
        mismatch = {"provider": "PROV1", "timestamp": "2020-01-01T00:00:00Z"}
        self.assertEqual(
            find_granules.report_file_name(mismatch),
            "PROV1/PROV1_missing_in_es_2020-01-01T00:00:00Z.csv",
        )

    def test_report_efs_file_name_uses_efs_path(self):
        mismatch = {"provider": "PROV1", "timestamp": "2020-01-01T00:00:00Z"}
        self.assertEqual(
            find_granules.report_efs_file_name(mismatch),
            "/efs/PROV1/PROV1_missing_in_es_2020-01-01T00:00:00Z.csv",
        )

    def test_db_batch_read_rejects_invalid_table_name(self):
        mismatch = {
            "provider": "BAD-NAME",  # '-' invalid per DB_TABLE_REGEX
            "concept_id": "C1",
            "timestamp": "2020-01-01T00:00:00+00:00",
        }

        # db_batch_read connects before validating; mock out DB connect to avoid sys.exit
        dummy_conn = DummyDBConnection(DummyCursorBatch(batches=[[]]))

        with mock.patch.object(find_granules, "connect_to_db", return_value=dummy_conn):
            with self.assertRaisesRegex(Exception, "is not valid"):
                next(find_granules.db_batch_read(mismatch))

    def test_db_batch_read_yields_batches_and_closes_connection(self):
        mismatch = {
            "provider": "PROV1",
            "concept_id": "C1",
            "timestamp": "2020-01-01T00:00:00+00:00",
        }

        cursor = DummyCursorBatch(
            batches=[
                [("G1", 1, "t1"), ("G2", 2, "t2")],
                [("G3", 3, "t3")],
                [],
            ]
        )
        conn = DummyDBConnection(cursor)

        with mock.patch.object(find_granules, "connect_to_db", return_value=conn):
            gen = find_granules.db_batch_read(mismatch)
            b1 = next(gen)
            b2 = next(gen)
            self.assertEqual(len(b1), 2)
            self.assertEqual(len(b2), 1)
            # Exhaust generator
            with self.assertRaises(StopIteration):
                next(gen)

        self.assertTrue(conn.closed)
        self.assertTrue(cursor.executed)

    def test_has_granule_been_deleted_returns_value(self):
        cursor = DummyCursorFetchone(fetchone_value=(1,))
        conn = DummyDBConnection(cursor)

        deleted = find_granules.has_granule_been_deleted(conn, "PROV1", "G123")
        self.assertEqual(deleted, 1)
        self.assertTrue(cursor.executed)
        _, params = cursor.executed[0]
        self.assertEqual(params["cid"], "G123")

    def test_execute_es_query_success(self):
        with mock.patch.object(find_granules, "es_health_check", lambda: None):
            with mock.patch.object(
                find_granules.requests,
                "post",
                return_value=DummyResponse(200, json_data={"hits": {"total": {"value": 3}}}),
            ) as post:
                out = find_granules.execute_es_query("idx", {"query": {}})
                self.assertEqual(out["hits"]["total"]["value"], 3)
                post.assert_called_once()

    def test_execute_es_query_exits_on_error_status(self):
        with mock.patch.object(find_granules, "es_health_check", lambda: None):
            with mock.patch.object(
                find_granules.requests,
                "post",
                return_value=DummyResponse(500, json_data={"error": "x"}, text="boom"),
            ):
                with self.assertRaises(SystemExit):
                    find_granules.execute_es_query("idx", {"query": {}})

    def test_get_collection_entry_title_returns_entry_title(self):
        with mock.patch.object(find_granules, "es_health_check", lambda: None):
            with mock.patch.object(
                find_granules.requests,
                "post",
                return_value=DummyResponse(
                    200,
                    json_data={"hits": {"hits": [{"_source": {"entry-title": "My Title"}}]}},
                ),
            ):
                title = find_granules.get_collection_entry_title({"concept_id": "C1"})
                self.assertEqual(title, "My Title")

    def test_get_collection_entry_title_exits_on_error(self):
        with mock.patch.object(find_granules, "es_health_check", lambda: None):
            with mock.patch.object(
                find_granules.requests,
                "post",
                return_value=DummyResponse(503, json_data={"error": "x"}, text="down"),
            ):
                with self.assertRaises(SystemExit):
                    find_granules.get_collection_entry_title({"concept_id": "C1"})

    def test_publish_message_to_sqs_sends_compact_json(self):
        sqs = mock.Mock()
        sqs.send_message.return_value = {"MessageId": "mid-123"}  # <-- add this

        with mock.patch.object(find_granules, "SQS", sqs):
            find_granules.publish_message_to_sqs({"a": 1, "b": "x"})

        sqs.send_message.assert_called_once()
        _, kwargs = sqs.send_message.call_args
        self.assertEqual(kwargs["QueueUrl"], "http://example-sqs-queue-url")
        # separators=(',', ':') means no spaces
        self.assertEqual(kwargs["MessageBody"], '{"a":1,"b":"x"}')

    def test_prepare_report_file_creates_header(self):
        with tempfile.TemporaryDirectory() as td:
            with mock.patch.object(find_granules, "CONFIG", {**find_granules.CONFIG, "EFS_PATH": td + "/"}):
                mismatch = {"provider": "PROV1", "timestamp": "2020-01-01T00:00:00Z"}
                find_granules.prepare_report_file(mismatch)

                path = find_granules.report_efs_file_name(mismatch)
                with open(path, "r", encoding="UTF-8") as f:
                    header = f.readline().strip()
                self.assertEqual(
                    header,
                    "Collection Concept ID,Granule Concept ID,Granule Revision,Revision Date",
                )

    def test_process_mismatches_reads_json_and_calls_workers_and_upload(self):
        mismatches = [
            {
                "provider": "PROV1",
                "concept_id": "C1",
                "index": "idx",
                "db_count": 10,
                "es_count": 8,
                "timestamp": "2020-01-01T00:00:00Z",
            },
            {
                "provider": "PROV1",
                "concept_id": "C2",
                "index": "idx",
                "db_count": 5,
                "es_count": 4,
                "timestamp": "2020-01-01T00:00:00Z",
            },
        ]

        with mock.patch.object(find_granules.find_s3, "read_from_s3", return_value=json.dumps(mismatches)), \
             mock.patch.object(find_granules, "prepare_report_file") as prep, \
             mock.patch.object(find_granules, "process_db_batches") as process_batches, \
             mock.patch.object(find_granules.find_s3, "upload_file_to_s3") as upload:

            find_granules.process_mismatches(first_time_open_report=True)

            # prepare_report_file should be called once (only when first_time_open_report True)
            prep.assert_called_once()
            self.assertEqual(process_batches.call_count, 2)
            upload.assert_called_once()


if __name__ == "__main__":
    unittest.main(verbosity=2)