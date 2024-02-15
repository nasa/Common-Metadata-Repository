"""
Test module for unit testing job_router/lambda_function.py
"""
import unittest
from unittest.mock import patch
import os


from job_router import lambda_function

@patch('boto3.client')
class TestLambdaFunction(unittest.TestCase):
    """
    Unittest class
    """
    @patch.dict(os.environ, {}, clear=True)
    def test_cmr_environment_not_set(self, _mock_client):
        with self.assertRaises(SystemExit):
            lambda_function.handler({}, {})

    @patch.dict(os.environ, {"CMR_ENVIRONMENT": "test"}, clear=True)
    def test_cmr_url_not_set(self, _mock_client):
        with self.assertRaises(SystemExit):
            lambda_function.handler({}, {})

if __name__ == '__main__':
    unittest.main()
