import sys
import os

import unittest
from unittest.mock import patch, MagicMock
from io import StringIO
from access_control import AccessControl

class TestAccessControl(unittest.TestCase):
    def setUp(self):
        self.access_control = AccessControl()

    @patch.dict(os.environ, {"ACCESS_CONTROL_URL": "http://localhost:3011/access-control"})
    def test_get_url_from_parameter_store_local(self):
        self.access_control.get_url_from_parameter_store()
        self.assertEqual(self.access_control.url, "http://localhost:3011/access-control")

    @patch.dict(os.environ, {"ENVIRONMENT_NAME": "SIT"})
    @patch('access_control.Env_Vars')
    def test_get_url_from_parameter_store_aws(self, mock_env_vars):
        mock_env_vars_instance = MagicMock()
        mock_env_vars_instance.get_var.side_effect = [
            "https", "3011", "cmr.sit.earthdata.nasa.gov", "/access-control"
        ]
        mock_env_vars.return_value = mock_env_vars_instance

        self.access_control.get_url_from_parameter_store()
        expected_url = "https://cmr.sit.earthdata.nasa.gov:3011/access-control"
        self.assertEqual(self.access_control.url, expected_url)

    def test_get_url(self):
        with patch.object(AccessControl, 'get_url_from_parameter_store') as mock_method:
            self.access_control.url = "http://test-url.com"
            result = self.access_control.get_url()
            self.assertEqual(result, "http://test-url.com")
            mock_method.assert_not_called()

        with patch.object(AccessControl, 'get_url_from_parameter_store') as mock_method:
            self.access_control.url = None
            self.access_control.get_url()
            mock_method.assert_called_once()

    @patch('requests.get')
    def test_get_permissions(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.text = '{"C1200484253-CMR_ONLY":["read","update","delete","order"]}'
        mock_get.return_value = mock_response

        self.access_control.url = "http://test-url.com"
        result = self.access_control.get_permissions("user1", "C1200484253-CMR_ONLY")
        self.assertEqual(result, '{"C1200484253-CMR_ONLY":["read","update","delete","order"]}')

    @patch('requests.get')
    def test_get_permissions_failure(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 404
        mock_get.return_value = mock_response

        self.access_control.url = "http://test-url.com"
        result = self.access_control.get_permissions("user1", "C1200484253-CMR_ONLY")
        self.assertIsNone(result)

#    @patch.object(AccessControl, 'get_permissions')
#    def test_has_read_permission(self, mock_get_permissions):
        # Test when user has read permission
#        mock_get_permissions.return_value = {"C1200484253-CMR_ONLY": ["read", "update"]}
#        result = self.access_control.has_read_permission("user1", "C1200484253-CMR_ONLY")
#        self.assertTrue(result)

        # Test when user doesn't have read permission
#        mock_get_permissions.return_value = {"C1200484253-CMR_ONLY": ["update"]}
#        result = self.access_control.has_read_permission("user1", "C1200484253-CMR_ONLY")
#        self.assertFalse(result)

        # Test when concept_id is not in permissions
#        mock_get_permissions.return_value = {"C1200484253-OTHER": ["read"]}
#        result = self.access_control.has_read_permission("user1", "C1200484253-CMR_ONLY")
#        self.assertFalse(result)

        # Test when permissions is not a dictionary
#        mock_get_permissions.return_value = None
#        result = self.access_control.has_read_permission("user1", "C1200484253-CMR_ONLY")
#        self.assertFalse(result)

        # Test when get_permissions raises an exception
#        mock_get_permissions.side_effect = Exception("API Error")
#        result = self.access_control.has_read_permission("user1", "C1200484253-CMR_ONLY")
#        self.assertFalse(result)

    @patch('access_control.logger') 
    @patch.object(AccessControl, 'get_permissions')
    def test_has_read_permission_logging(self, mock_get_permissions, mock_logger):
        # Test logging when an exception occurs
        mock_get_permissions.side_effect = Exception("API Error")
        self.access_control.has_read_permission("user1", "C1200484253-CMR_ONLY")
        mock_logger.error.assert_called_once_with(
            "Subscription Worker Access Control error getting permissions for subscriber user1 on collection concept id C1200484253-CMR_ONLY: API Error"
        )

    def test_has_read_permission_integration(self):
        # This is an integration test that calls the actual get_permissions method
        # Note: This test depends on the actual API and may fail if the API is not available
        with patch.object(AccessControl, 'get_url', return_value='https://cmr.earthdata.nasa.gov/access-control'):
            result = self.access_control.has_read_permission("test_user", "C1234567-TEST")
            # Assert based on expected behavior. This might be True or False depending on the actual permissions
            self.assertIsInstance(result, bool)

if __name__ == '__main__':
    unittest.main()

