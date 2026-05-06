import json
import os
import unittest
import importlib.util
import sys
from pathlib import Path
from unittest.mock import Mock, patch

from botocore.exceptions import ClientError


def _load_find_s3_module():
    db_es_audit_root = Path(__file__).resolve().parents[1]
    src_dir = db_es_audit_root / "src"
    if str(src_dir) not in sys.path:
        sys.path.insert(0, str(src_dir))

    module_path = src_dir / "find_s3.py"
    spec = importlib.util.spec_from_file_location("find_s3", module_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)  # type: ignore[attr-defined]
    return module

find_s3 = _load_find_s3_module()


class UploadFileToS3Tests(unittest.TestCase):
    def setUp(self):
        find_s3.AUDIT_BUCKET_NAME = "test-audit-bucket"

    def test_upload_file_to_s3_success_object_name_defaulted_to_basename(self):
        mock_s3 = Mock()
        with patch.object(find_s3.boto3, "client", return_value=mock_s3):
            ok = find_s3.upload_file_to_s3("/mnt/efs/data.txt", object_name=None)

        self.assertTrue(ok)
        mock_s3.upload_file.assert_called_once_with(
            "/mnt/efs/data.txt", "test-audit-bucket", "data.txt"
        )

    def test_upload_file_to_s3_success_object_name_provided(self):
        mock_s3 = Mock()
        with patch.object(find_s3.boto3, "client", return_value=mock_s3):
            ok = find_s3.upload_file_to_s3("/mnt/efs/data.txt", object_name="x/y.json")

        self.assertTrue(ok)
        mock_s3.upload_file.assert_called_once_with(
            "/mnt/efs/data.txt", "test-audit-bucket", "x/y.json"
        )

    def test_upload_file_to_s3_client_error_returns_false(self):
        mock_s3 = Mock()
        mock_s3.upload_file.side_effect = ClientError(
            {"Error": {"Code": "AccessDenied", "Message": "nope"}},
            "UploadFile",
        )
        with patch.object(find_s3.boto3, "client", return_value=mock_s3):
            ok = find_s3.upload_file_to_s3("/mnt/efs/data.txt", object_name="data.txt")

        self.assertFalse(ok)

    def test_upload_file_to_s3_file_not_found_returns_false(self):
        mock_s3 = Mock()
        mock_s3.upload_file.side_effect = FileNotFoundError("missing")
        with patch.object(find_s3.boto3, "client", return_value=mock_s3):
            ok = find_s3.upload_file_to_s3("/mnt/efs/missing.txt", object_name="missing.txt")

        self.assertFalse(ok)


class SaveToS3Tests(unittest.TestCase):
    def setUp(self):
        find_s3.AUDIT_BUCKET_NAME = "test-audit-bucket"

    def test_save_to_s3_put_object_called_with_expected_key_and_json_body(self):
        s3_client = Mock()
        data = {"a": 1}
        provider = "PROV"

        find_s3.save_to_s3(s3_client, data, provider)

        s3_client.put_object.assert_called_once()
        kwargs = s3_client.put_object.call_args.kwargs
        self.assertEqual(kwargs["Bucket"], "test-audit-bucket")
        self.assertEqual(kwargs["Key"], "PROV/PROV_collections.json")
        self.assertEqual(kwargs["ContentType"], "application/json")
        self.assertEqual(kwargs["Body"], json.dumps(data).encode("utf-8"))

    def test_save_providers_to_s3_put_object_called_with_expected_key_and_json_body(self):
        s3_client = Mock()
        data = [{"id": "P1"}]

        find_s3.save_providers_to_s3(s3_client, data)

        s3_client.put_object.assert_called_once()
        kwargs = s3_client.put_object.call_args.kwargs
        self.assertEqual(kwargs["Bucket"], "test-audit-bucket")
        self.assertEqual(kwargs["Key"], "providers.json")
        self.assertEqual(kwargs["ContentType"], "application/json")
        self.assertEqual(kwargs["Body"], json.dumps(data).encode("utf-8"))


class ReadFromS3Tests(unittest.TestCase):
    def setUp(self):
        find_s3.AUDIT_BUCKET_NAME = "test-audit-bucket"

    # ... existing code ...

    def test_read_from_s3_exception_logs_and_raises(self):
        s3_client = Mock()
        s3_client.get_object.side_effect = Exception("boom")

        with patch.object(find_s3, "logger") as mock_logger:
            with self.assertRaises(Exception) as ctx:
                find_s3.read_from_s3(s3_client, "some/key.json")

        self.assertIn("boom", str(ctx.exception))
        mock_logger.exception.assert_called()

class ReadJsonHelpersTests(unittest.TestCase):
    def test_read_providers_from_s3_loads_json_from_read_from_s3(self):
        s3_client = Mock()
        with patch.object(find_s3, "read_from_s3", return_value='["P1","P2"]') as m:
            out = find_s3.read_providers_from_s3(s3_client)

        self.assertEqual(out, ["P1", "P2"])
        m.assert_called_once_with(s3_client, "providers.json")

    def test_read_collections_from_provider_loads_json_from_read_from_s3(self):
        s3_client = Mock()
        with patch.object(find_s3, "read_from_s3", return_value='{"c": [1]}') as m:
            out = find_s3.read_collections_from_provider(s3_client, "PROV")

        self.assertEqual(out, {"c": [1]})
        m.assert_called_once_with(s3_client, "PROV/PROV_collections.json")

