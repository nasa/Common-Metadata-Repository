import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'src')))

import unittest
from unittest.mock import patch, MagicMock
from io import StringIO
from access_control import AccessControl

class TestAccessControl(unittest.TestCase):
    def setUp(self):
        self.ac = AccessControl()

    @patch.dict(os.environ, {"ENVIRONMENT_NAME": "test_env"})
    @patch('access_control.Env_Vars')
    def test_get_url_from_parameter_store(self, mock_env_vars):
        mock_env_vars.get_var.side_effect = [
            "https", "443", "example.com", "api/v1"
        ]

        self.ac.get_url_from_parameter_store()

        self.assertEqual(self.ac.url, "https://example.com:443/api/v1")

    @patch.dict(os.environ, {})
    def test_get_url_from_parameter_store_no_env(self):
        with self.assertRaises(ValueError):
            self.ac.get_url_from_parameter_store()

    @patch('access_control.AccessControl.get_url_from_parameter_store')
    def test_get_url(self, mock_get_url):
        mock_get_url.return_value = None
        self.ac.url = "https://test.com"

        # Exercise
        result = self.ac.get_url()

        # Verify
        self.assertEqual(result, "https://test.com")
        mock_get_url.assert_not_called()

    @patch('access_control.AccessControl.get_url_from_parameter_store')
    def test_get_url_not_set(self, mock_get_url):
        mock_get_url.return_value = None
        self.ac.url = None

        # Exercise
        self.ac.get_url()

        # Verify
        mock_get_url.assert_called_once()

    @patch('access_control.requests.get')
    @patch('access_control.AccessControl.get_url')
    def test_get_permissions_success(self, mock_get_url, mock_requests_get):
        mock_get_url.return_value = "https://test.com"
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.text = '{"permissions": ["read", "write"]}'
        mock_requests_get.return_value = mock_response

        # Exercise
        result = self.ac.get_permissions("sub123", "concept456")

        # Verify
        self.assertEqual(result, '{"permissions": ["read", "write"]}')
        mock_requests_get.assert_called_once_with(
            "https://test.com/permissions",
            params={"user_id": "sub123", "concept_id": "concept456"}
        )

    @patch('access_control.requests.get')
    @patch('access_control.AccessControl.get_url')
    def test_get_permissions_failure(self, mock_get_url, mock_requests_get):
        mock_get_url.return_value = "https://test.com"
        mock_response = MagicMock()
        mock_response.status_code = 404
        mock_requests_get.return_value = mock_response

        # Exercise
        result = self.ac.get_permissions("sub123", "concept456")

        # Verify
        self.assertIsNone(result)
        mock_requests_get.assert_called_once_with(
            "https://test.com/permissions",
            params={"user_id": "sub123", "concept_id": "concept456"}
        )

if __name__ == '__main__':
    unittest.main()