import io
import json
import os
import sys
import unittest
from unittest import mock

# Make top-level ./src importable
TOP_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(TOP_DIR, "src")
if SRC_DIR not in sys.path:
    sys.path.insert(0, SRC_DIR)

import find_s3  # noqa: E402


class FindS3Tests(unittest.TestCase):
    def setUp(self):
        self.config_patcher = mock.patch.object(
            find_s3,
            "CONFIG",
            {"AUDIT_S3_BUCKET_NAME": "my-audit-bucket"},
        )
        self.config_patcher.start()

    def tearDown(self):
        self.config_patcher.stop()

    def test_save_to_s3_puts_expected_object(self):
        s3_client = mock.Mock()
        find_s3.save_to_s3(s3_client, {"PROV1": ["C1", "C2"]}, "PROV1")

        s3_client.put_object.assert_called_once()
        _, kwargs = s3_client.put_object.call_args
        self.assertEqual(kwargs["Bucket"], "my-audit-bucket")
        self.assertEqual(kwargs["Key"], "PROV1/PROV1_collections.json")
        self.assertEqual(kwargs["ContentType"], "application/json")
        self.assertEqual(json.loads(kwargs["Body"].decode("utf-8")), {"PROV1": ["C1", "C2"]})

    def test_save_providers_to_s3_puts_expected_object(self):
        s3_client = mock.Mock()
        find_s3.save_providers_to_s3(s3_client, ["P1", "P2"])

        s3_client.put_object.assert_called_once()
        _, kwargs = s3_client.put_object.call_args
        self.assertEqual(kwargs["Bucket"], "my-audit-bucket")
        self.assertEqual(kwargs["Key"], "providers.json")
        self.assertEqual(kwargs["ContentType"], "application/json")
        self.assertEqual(json.loads(kwargs["Body"].decode("utf-8")), ["P1", "P2"])

    def test_read_from_s3_reads_and_decodes_body(self):
        s3_client = mock.Mock()
        s3_client.get_object.return_value = {
            "Body": io.BytesIO(b'{"hello":"world"}')
        }

        out = find_s3.read_from_s3(s3_client, "some/key.json")
        self.assertEqual(out, '{"hello":"world"}')

        s3_client.get_object.assert_called_once_with(
            Bucket="my-audit-bucket",
            Key="some/key.json",
        )

    def test_read_from_s3_reraises_exception(self):
        s3_client = mock.Mock()
        s3_client.get_object.side_effect = RuntimeError("boom")

        with self.assertRaises(RuntimeError):
            find_s3.read_from_s3(s3_client, "x.json")

    def test_read_providers_from_s3_parses_json(self):
        s3_client = mock.Mock()
        with mock.patch.object(find_s3, "read_from_s3", return_value='["P1","P2"]') as read:
            out = find_s3.read_providers_from_s3(s3_client)
        self.assertEqual(out, ["P1", "P2"])
        read.assert_called_once_with(s3_client, "providers.json")

    def test_read_collections_from_provider_parses_json(self):
        s3_client = mock.Mock()
        with mock.patch.object(find_s3, "read_from_s3", return_value='{"PROV1":["C1"]}') as read:
            out = find_s3.read_collections_from_provider(s3_client, "PROV1")
        self.assertEqual(out, {"PROV1": ["C1"]})
        read.assert_called_once_with(s3_client, "PROV1/PROV1_collections.json")

    def test_upload_file_to_s3_uses_basename_when_object_name_none(self):
        s3_client = mock.Mock()
        with mock.patch.object(find_s3.boto3, "client", return_value=s3_client):
            ok = find_s3.upload_file_to_s3("/efs/dir/file.txt", object_name=None)

        self.assertTrue(ok)
        s3_client.upload_file.assert_called_once_with(
            "/efs/dir/file.txt",
            "my-audit-bucket",
            "file.txt",
        )

    def test_upload_file_to_s3_uses_object_name_when_provided(self):
        s3_client = mock.Mock()
        with mock.patch.object(find_s3.boto3, "client", return_value=s3_client):
            ok = find_s3.upload_file_to_s3("/efs/dir/file.txt", object_name="a/b/c.txt")

        self.assertTrue(ok)
        s3_client.upload_file.assert_called_once_with(
            "/efs/dir/file.txt",
            "my-audit-bucket",
            "a/b/c.txt",
        )

    def test_upload_file_to_s3_returns_false_on_client_error(self):
        # Patch ClientError in find_s3 module so we don't need botocore in the test env
        class FakeClientError(Exception):
            pass

        s3_client = mock.Mock()
        s3_client.upload_file.side_effect = FakeClientError("nope")

        with mock.patch.object(find_s3, "ClientError", FakeClientError), \
             mock.patch.object(find_s3.boto3, "client", return_value=s3_client):
            ok = find_s3.upload_file_to_s3("/efs/dir/file.txt", object_name="x.txt")

        self.assertFalse(ok)

    def test_upload_file_to_s3_returns_false_on_file_not_found(self):
        s3_client = mock.Mock()
        s3_client.upload_file.side_effect = FileNotFoundError()

        with mock.patch.object(find_s3.boto3, "client", return_value=s3_client):
            ok = find_s3.upload_file_to_s3("/efs/dir/missing.txt", object_name="x.txt")

        self.assertFalse(ok)


if __name__ == "__main__":
    unittest.main(verbosity=2)