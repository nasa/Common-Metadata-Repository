import io
import json
import os
import sys
import types
import unittest
from unittest import mock


# Ensure ENVIRONMENT exists before importing the module (module exits at import if missing)
os.environ.setdefault("ENVIRONMENT", "test")

# Make top-level ./src importable
TOP_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(TOP_DIR, "src")
if SRC_DIR not in sys.path:
    sys.path.insert(0, SRC_DIR)

# If oracledb isn't installed in the test env, provide a minimal stub so import works
if "oracledb" not in sys.modules:
    sys.modules["oracledb"] = types.SimpleNamespace(Error=Exception)

import find_granule_counts  # noqa: E402


class DummyResponse:
    def __init__(self, status_code=200, json_data=None, text=""):
        self.status_code = status_code
        self._json_data = json_data if json_data is not None else {}
        self.text = text

    def json(self):
        return self._json_data

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"HTTP {self.status_code}")


class DummyCursor:
    def __init__(self, fetchone_value=None, fetchall_value=None):
        self._fetchone_value = fetchone_value
        self._fetchall_value = fetchall_value or []
        self.executed = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, query, **params):
        self.executed.append((query, params))

    def fetchone(self):
        return self._fetchone_value

    def fetchall(self):
        return self._fetchall_value


class DummyDBConnection:
    def __init__(self, cursor):
        self._cursor = cursor

    def cursor(self):
        return self._cursor


class FindGranuleCountsTests(unittest.TestCase):
    def setUp(self):
        # Override CONFIG inside module namespace for deterministic tests
        self._config_patcher = mock.patch.object(
            find_granule_counts,
            "CONFIG",
            {
                "GRAN_ELASTIC_URL": "http://example-es",
                "EFS_PATH": "/efs/",
            },
        )
        self._config_patcher.start()

    def tearDown(self):
        self._config_patcher.stop()

    def test_find_index_matches_collection_alias(self):
        indices = ["c1234_abcd__alias", "c5678_efgh__alias"]
        self.assertEqual(
            find_granule_counts.find_index(indices, "C1234-ABCD"),
            "c1234_abcd__alias",
        )

    def test_find_index_defaults_to_small_collections_alias(self):
        indices = ["something_else_alias"]
        self.assertEqual(
            find_granule_counts.find_index(indices, "C9999-NOPE"),
            "1_small_collections_alias",
        )

    def test_missing_file_name(self):
        self.assertEqual(
            find_granule_counts.missing_file_name("PROV1"),
            "PROV1/PROV1_granule_counts_mismatch.json",
        )

    def test_missing_efs_file_name_uses_efs_path(self):
        self.assertEqual(
            find_granule_counts.missing_efs_file_name("PROV1"),
            "/efs/PROV1/PROV1_granule_counts_mismatch.json",
        )

    def test_get_es_indices(self):
        with mock.patch.object(find_granule_counts, "es_health_check", lambda: None):

            def fake_get(url, timeout):
                self.assertEqual(
                    url, "http://example-es/_cat/aliases?h=alias&format=json"
                )
                return DummyResponse(
                    200, json_data=[{"alias": "a1"}, {"alias": "a2"}, {"alias": "a3"}]
                )

            with mock.patch.object(find_granule_counts.requests, "get", side_effect=fake_get):
                self.assertEqual(find_granule_counts.get_es_indices(), ["a1", "a2", "a3"])

    def test_get_es_granule_count_happy_path(self):
        with mock.patch.object(find_granule_counts, "es_health_check", lambda: None):

            def fake_get(url, data, headers, timeout):
                self.assertEqual(url, "http://example-es/my_index/_search")
                body = json.loads(data)
                self.assertEqual(body["size"], 0)
                self.assertTrue(body["track_total_hits"])
                return DummyResponse(200, json_data={"hits": {"total": {"value": 42}}})

            with mock.patch.object(find_granule_counts.requests, "get", side_effect=fake_get):
                n = find_granule_counts.get_es_granule_count(
                    "my_index", "C1234-ABCD", "2020-01-01T00:00:00Z"
                )
                self.assertEqual(n, 42)

    def test_get_es_granule_count_exits_on_http_error(self):
        with mock.patch.object(find_granule_counts, "es_health_check", lambda: None):
            with mock.patch.object(
                find_granule_counts.requests,
                "get",
                return_value=DummyResponse(500, json_data={"error": "nope"}, text="boom"),
            ):
                with self.assertRaises(SystemExit):
                    find_granule_counts.get_es_granule_count(
                        "idx", "C1-TEST", "2020-01-01T00:00:00Z"
                    )

    def test_get_db_granule_count_uses_cursor_fetchone(self):
        cur = DummyCursor(fetchone_value=(7,))
        db = DummyDBConnection(cur)

        n = find_granule_counts.get_db_granule_count(
            db_connection=db,
            latest_working_time="2020-01-01T00:00:00Z",
            provider="PROV1",
            collection_concept_id="C1234-ABCD",
        )
        self.assertEqual(n, 7)
        self.assertTrue(cur.executed)

    def test_compare_granule_counts_writes_only_mismatches(self):
        db_counts = {"C1": 10, "C2": 10, "C3": 1}
        es_counts = {"C1": 10, "C2": 8, "C3": 2}

        # minimal object that supports latest_working_time.isoformat()
        latest_working_time = types.SimpleNamespace(
            isoformat=lambda: "2020-01-01T00:00:00+00:00"
        )

        with mock.patch.object(find_granule_counts, "get_es_indices", return_value=["idx_alias"]), \
             mock.patch.object(
                 find_granule_counts,
                 "get_db_granule_count",
                 side_effect=lambda _db, _t, _prov, cid: db_counts[cid],
             ), \
             mock.patch.object(find_granule_counts, "find_index", side_effect=lambda _indices, _cid: "idx"), \
             mock.patch.object(
                 find_granule_counts,
                 "get_es_granule_count",
                 side_effect=lambda _idx, cid, _t: es_counts[cid],
             ):

            f = io.StringIO()
            f.write("[")
            find_granule_counts.compare_granule_counts(
                db_connection=object(),
                latest_working_time=latest_working_time,
                f=f,
                provider="PROV1",
                collection_concept_ids=["C1", "C2", "C3"],
            )
            f.write("]")

            data = json.loads(f.getvalue())
            self.assertEqual([d["concept_id"] for d in data], ["C2", "C3"])
            self.assertEqual((data[0]["db_count"], data[0]["es_count"]), (10, 8))
            self.assertEqual((data[1]["db_count"], data[1]["es_count"]), (1, 2))

    def test_get_current_cluster_name_raises_outside_fargate(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(Exception, "Not running inside a Fargate environment"):
                find_granule_counts.get_current_cluster_name()

    def test_get_current_fargate_network_config_raises_outside_fargate(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(Exception, "Not running inside a Fargate environment"):
                find_granule_counts.get_current_fargate_network_config()


if __name__ == "__main__":
    unittest.main(verbosity=2)