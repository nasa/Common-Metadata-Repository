import importlib
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch


class FindGranulesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Make db-es-audit/src importable as a top-level module path
        repo_root = Path(__file__).resolve().parents[1]
        src_dir = repo_root / "db-es-audit" / "src"
        sys.path.insert(0, str(src_dir))

    def setUp(self):
        # Set required env vars BEFORE importing/reloading find_granules
        self.env_patcher = patch.dict(
            os.environ,
            {
                "PROVIDER": "TESTPROV",
                "ELASTIC_HOST": "localhost",
                "ELASTIC_PORT": "9200",
                "GRAN_ELASTIC_HOST": "localhost",
                "GRAN_ELASTIC_PORT": "9200",
                "SQS_QUEUE_URL": "https://sqs.us-east-1.amazonaws.com/123/q",
                "EFS_PATH": "/tmp/efs/",
                "BATCH_SIZE": "1000",
            },
            clear=False,
        )
        self.env_patcher.start()

        import find_granules  # from db-es-audit/src via sys.path
        self.fg = importlib.reload(find_granules)

    def tearDown(self):
        self.env_patcher.stop()

    def test_elastic_search_query_builds_should_terms(self):
        db_batch = [("G1", 10, "2020-01-01"), ("G2", 11, "2020-01-02")]
        q = self.fg.elastic_search_query(db_batch, query_size=0)

        self.assertEqual(q["size"], 0)
        should = q["query"]["bool"]["should"]
        self.assertEqual(len(should), 2)
        self.assertEqual(should[0]["bool"]["must"][0]["term"]["concept-id"], "G1")
        self.assertEqual(should[0]["bool"]["must"][1]["term"]["revision-id"], "10")

    def test_execute_es_query_success(self):
        fake_resp = MagicMock()
        fake_resp.status_code = 200
        fake_resp.json.return_value = {"hits": {"total": {"value": 1}}}

        with patch.object(self.fg.requests, "post", return_value=fake_resp):
            out = self.fg.execute_es_query("myindex", {"query": {}})
        self.assertEqual(out["hits"]["total"]["value"], 1)

    def test_publish_message_to_sqs(self):
        fake_sqs = MagicMock()
        fake_sqs.send_message.return_value = {"MessageId": "abc-123"}

        with patch.object(self.fg, "SQS", fake_sqs):
            self.fg.publish_message_to_sqs({"a": 1})

        kwargs = fake_sqs.send_message.call_args.kwargs
        self.assertEqual(kwargs["QueueUrl"], os.environ["SQS_QUEUE_URL"])
        self.assertEqual(kwargs["MessageBody"], '{"a":1}')

