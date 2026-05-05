import io
import json
import sys
import types
import unittest
import importlib.util
from pathlib import Path
from unittest.mock import Mock, call, mock_open, patch


def _install_stub_modules_for_import():
    """
    find_granule_counts.py imports modules that may not be installed in the test env
    (e.g., oracledb). Provide minimal stubs so the module can be imported.
    """
    if "oracledb" not in sys.modules:
        m = types.ModuleType("oracledb")

        class OracleError(Exception):
            pass

        m.Error = OracleError
        sys.modules["oracledb"] = m

    # If these exist in src, they'll be used; if not, stubs prevent import errors.
    if "find_db" not in sys.modules:
        m = types.ModuleType("find_db")
        m.connect_to_db = lambda: None
        sys.modules["find_db"] = m

    if "find_logger" not in sys.modules:
        m = types.ModuleType("find_logger")
        m.setup_logging = lambda: None
        sys.modules["find_logger"] = m


def _load_find_granule_counts_module():
    _install_stub_modules_for_import()

    # test/ is alongside src/ inside db-es-audit/
    db_es_audit_root = Path(__file__).resolve().parents[1]
    src_dir = db_es_audit_root / "src"
    if str(src_dir) not in sys.path:
        sys.path.insert(0, str(src_dir))

    module_path = src_dir / "find_granule_counts.py"
    spec = importlib.util.spec_from_file_location("find_granule_counts", module_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)  # type: ignore[attr-defined]
    return module


find_granule_counts = _load_find_granule_counts_module()


class GetProvidersTests(unittest.TestCase):
    def _cursor_cm(self, cursor):
        cursor.__enter__ = Mock(return_value=cursor)
        cursor.__exit__ = Mock(return_value=False)
        return cursor

    def test_get_providers_filters_tables_with_rows_and_saves_to_s3(self):
        db = Mock()
        s3_client = Mock()

        c1 = self._cursor_cm(Mock())
        c1.fetchall.return_value = [("P1_GRANULES",), ("P2_GRANULES",)]

        c2 = self._cursor_cm(Mock())
        c2.fetchone.side_effect = [(1,), (0,)]  # P1 has rows, P2 does not

        db.cursor.side_effect = [c1, c2]

        with patch.object(find_granule_counts.find_s3, "save_providers_to_s3") as save_mock:
            out = find_granule_counts.get_providers(db, s3_client)

        self.assertEqual(out, ["P1"])
        save_mock.assert_called_once_with(s3_client, ["P1"])

    def test_get_providers_raises_on_oracle_error_fetching_table_list(self):
        db = Mock()
        s3_client = Mock()

        c1 = self._cursor_cm(Mock())
        err_type = find_granule_counts.oracledb.Error
        c1.execute.side_effect = err_type("db down")
        db.cursor.side_effect = [c1]

        with self.assertRaises(Exception) as ctx:
            find_granule_counts.get_providers(db, s3_client)

        self.assertIn("Failed to execute query", str(ctx.exception))


class GetCollectionsPerProviderTests(unittest.TestCase):
    def _cursor_cm(self, cursor):
        cursor.__enter__ = Mock(return_value=cursor)
        cursor.__exit__ = Mock(return_value=False)
        return cursor

    def test_get_collections_per_provider_saves_each_provider_map_to_s3(self):
        db = Mock()
        s3 = Mock()
        curr = self._cursor_cm(Mock())
        db.cursor.return_value = curr

        curr.fetchall.side_effect = [
            [("C1",), ("C2",)],  # P1
            [("D1",)],           # P2
        ]

        with patch.object(find_granule_counts.find_s3, "save_to_s3") as save_to_s3:
            find_granule_counts.get_collections_per_provider(db, s3, ["P1", "P2"])

        self.assertEqual(save_to_s3.call_args_list, [
            call(s3, {"P1": ["C1", "C2"]}, "P1"),
            call(s3, {"P2": ["D1"]}, "P2"),
        ])

    def test_get_collections_per_provider_logs_error_and_continues_on_oracle_error(self):
        db = Mock()
        s3 = Mock()
        curr = self._cursor_cm(Mock())
        db.cursor.return_value = curr

        err_type = find_granule_counts.oracledb.Error
        curr.execute.side_effect = [err_type("bad table"), None]
        curr.fetchall.side_effect = [[("OK1",)]]

        with patch.object(find_granule_counts, "logger") as log_mock, \
             patch.object(find_granule_counts.find_s3, "save_to_s3") as save_to_s3:
            find_granule_counts.get_collections_per_provider(db, s3, ["P1", "P2"])

        log_mock.error.assert_called()
        save_to_s3.assert_called_once_with(s3, {"P2": ["OK1"]}, "P2")


class GetProvidersWithCollectionsTests(unittest.TestCase):
    def test_get_providers_with_collections_wires_db_s3_and_calls_helpers(self):
        mock_db = Mock()
        mock_s3 = Mock()

        with patch.object(find_granule_counts, "connect_to_db", return_value=mock_db) as ctdb, \
             patch.object(find_granule_counts.boto3, "client", return_value=mock_s3) as b3c, \
             patch.object(find_granule_counts, "get_providers", return_value=["P1"]) as gp, \
             patch.object(find_granule_counts, "get_collections_per_provider") as gcpp:

            find_granule_counts.get_providers_with_collections()

        ctdb.assert_called_once()
        b3c.assert_called_once_with("s3")
        gp.assert_called_once_with(mock_db, mock_s3)
        gcpp.assert_called_once_with(mock_db, mock_s3, ["P1"])
        mock_db.close.assert_called_once()


class GetDbGranuleCountTests(unittest.TestCase):
    def _cursor_cm(self, cursor):
        cursor.__enter__ = Mock(return_value=cursor)
        cursor.__exit__ = Mock(return_value=False)
        return cursor

    def test_get_db_granule_count_returns_count(self):
        db = Mock()
        curr = self._cursor_cm(Mock())
        db.cursor.return_value = curr
        curr.fetchone.return_value = (5,)

        from datetime import datetime
        lwt = datetime(2020, 1, 1, 0, 0, 0)

        out = find_granule_counts.get_db_granule_count(db, lwt, "P1", "C1")
        self.assertEqual(out, 5)

    def test_get_db_granule_count_returns_zero_on_oracle_error(self):
        db = Mock()
        curr = self._cursor_cm(Mock())
        db.cursor.return_value = curr
        err_type = find_granule_counts.oracledb.Error
        curr.execute.side_effect = err_type("nope")

        from datetime import datetime
        lwt = datetime(2020, 1, 1, 0, 0, 0)

        out = find_granule_counts.get_db_granule_count(db, lwt, "P1", "C1")
        self.assertEqual(out, 0)


class ElasticHelpersTests(unittest.TestCase):
    def test_get_es_indices_returns_index_list(self):
        resp = Mock()
        resp.json.return_value = [{"alias": "i1"}, {"alias": "i2"}]

        with patch.object(find_granule_counts.requests, "get", return_value=resp) as rg:
            out = find_granule_counts.get_es_indices()

        self.assertEqual(out, ["i1", "i2"])
        rg.assert_called_once()

    def test_find_index_returns_match_or_default(self):
        indices = ["c123_abc", "foo"]
        self.assertEqual(find_granule_counts.find_index(indices, "C123-ABC"), "c123_abc")
        self.assertEqual(find_granule_counts.find_index(indices, "NO-MATCH"), "1_small_collections_alias")

    def test_get_es_granule_count_success(self):
        resp = Mock()
        resp.status_code = 200
        resp.json.return_value = {"hits": {"total": {"value": 7}}}

        with patch.object(find_granule_counts.requests, "get", return_value=resp):
            out = find_granule_counts.get_es_granule_count("idx", "C1", "2020-01-01T00:00:00")

        self.assertEqual(out, 7)

    def test_get_es_granule_count_http_error_exits(self):
        resp = Mock()
        resp.status_code = 500
        resp.text = "broken"

        with patch.object(find_granule_counts.requests, "get", return_value=resp), \
             self.assertRaises(SystemExit):
            find_granule_counts.get_es_granule_count("idx", "C1", "2020-01-01T00:00:00")


class CompareGranuleCountsTests(unittest.TestCase):
    def test_compare_granule_counts_writes_only_mismatches_and_uses_commas_between(self):
        db = Mock()
        from datetime import datetime
        lwt = datetime(2020, 1, 1, 0, 0, 0)

        collection_ids = ["C0", "C1", "C2"]

        with patch.object(find_granule_counts, "get_es_indices", return_value=["c1", "c2"]), \
             patch.object(find_granule_counts, "find_index", side_effect=["idx1", "idx2"]), \
             patch.object(find_granule_counts, "get_db_granule_count", side_effect=[0, 10, 5]), \
             patch.object(find_granule_counts, "get_es_granule_count", side_effect=[7, 1]):

            f = io.StringIO()
            find_granule_counts.compare_granule_counts(db, lwt, f, "P1", collection_ids)
            s = f.getvalue().replace("\n", "")

        self.assertIn('"concept_id": "C1"', s)
        self.assertIn('"concept_id": "C2"', s)
        self.assertIn("}, {", s)  # comma delimiter from the code path
        self.assertNotIn('"concept_id": "C0"', s)


class FileNameHelpersTests(unittest.TestCase):
    def test_missing_file_name(self):
        self.assertEqual(
            find_granule_counts.missing_file_name("P1"),
            "P1/P1_granule_counts_mismatch.json",
        )

    def test_missing_efs_file_name_uses_efs_path_prefix(self):
        old = find_granule_counts.EFS_PATH
        try:
            find_granule_counts.EFS_PATH = "/mnt/efs/"
            self.assertEqual(
                find_granule_counts.missing_efs_file_name("P1"),
                "/mnt/efs/P1/P1_granule_counts_mismatch.json",
            )
        finally:
            find_granule_counts.EFS_PATH = old


class FindGranuleCountsMismatchTests(unittest.TestCase):
    def test_find_granule_counts_mismatch_creates_file_runs_compare_and_uploads(self):
        old_efs = find_granule_counts.EFS_PATH
        find_granule_counts.EFS_PATH = "/tmp/efs/"

        provider = "P1"
        mock_db = Mock()
        mock_s3 = Mock()
        collections_map = {provider: ["C1", "C2"]}

        m = mock_open()
        with patch.object(find_granule_counts, "connect_to_db", return_value=mock_db), \
             patch.object(find_granule_counts.boto3, "client", return_value=mock_s3), \
             patch.object(find_granule_counts.os, "makedirs") as makedirs, \
             patch.object(find_granule_counts.find_s3, "read_collections_from_provider", return_value=collections_map), \
             patch.object(find_granule_counts, "compare_granule_counts") as compare_mock, \
             patch.object(find_granule_counts.find_s3, "upload_file_to_s3", return_value=True) as upload_mock, \
             patch("builtins.open", m):

            find_granule_counts.find_granule_counts_mismatch(provider)

        makedirs.assert_called_once_with("/tmp/efs/P1", exist_ok=True)

        expected_efs_path = "/tmp/efs/P1/P1_granule_counts_mismatch.json"
        m.assert_called_with(expected_efs_path, "w", encoding="UTF-8")
        self.assertTrue(compare_mock.called)

        upload_mock.assert_called_once_with(
            expected_efs_path,
            "P1/P1_granule_counts_mismatch.json",
        )

        find_granule_counts.EFS_PATH = old_efs

