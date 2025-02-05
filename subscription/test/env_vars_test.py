import unittest
import os
from unittest.mock import patch, MagicMock
from botocore.exceptions import ClientError
from env_vars import Env_Vars

class TestEnvVars(unittest.TestCase):
    def setUp(self):
        self.env_vars = Env_Vars()

    @patch.dict(os.environ, {'TEST_VAR': 'test_value'})
    def test_get_var_from_os(self):
        value = self.env_vars.get_var('TEST_VAR')
        self.assertEqual(value, 'test_value')

    @patch.dict(os.environ, {}, clear=True)
    @patch('boto3.client')
    def test_get_var_from_ssm(self, mock_boto3_client):
        mock_ssm = MagicMock()
        mock_boto3_client.return_value = mock_ssm
        mock_ssm.get_parameter.return_value = {
            'Parameter': {
                'Value': 'some_value'
            }
        }

        self.env_vars.ssm_client = mock_ssm

        value = self.env_vars.get_var('SOME_VAR')
        self.assertEqual(value, 'some_value')
        mock_ssm.get_parameter.assert_called_once_with(Name='SOME_VAR', WithDecryption=False)

    @patch.dict(os.environ, {}, clear=True)
    @patch('boto3.client')
    def test_get_var_with_decryption(self, mock_boto3_client):
        mock_ssm = MagicMock()
        mock_boto3_client.return_value = mock_ssm
        mock_ssm.get_parameter.return_value = {
            'Parameter': {
                'Value': 'encrypted_value'
            }
        }

        self.env_vars.ssm_client = mock_ssm

        value = self.env_vars.get_var('ENCRYPTED_VAR', decryption=True)
        self.assertEqual(value, 'encrypted_value')
        mock_ssm.get_parameter.assert_called_once_with(Name='ENCRYPTED_VAR', WithDecryption=True)

    @patch.dict(os.environ, {}, clear=True)
    @patch('boto3.client')
    def test_get_var_ssm_error(self, mock_boto3_client):
        mock_ssm = MagicMock()
        mock_boto3_client.return_value = mock_ssm
        mock_ssm.get_parameter.side_effect = ClientError(
            {'Error': {'Code': 'ParameterNotFound', 'Message': 'Parameter not found.'}},
            'GetParameter'
        )

        self.env_vars.ssm_client = mock_ssm

        with self.assertRaises(ClientError):
            self.env_vars.get_var('NONEXISTENT_VAR')

if __name__ == '__main__':
    unittest.main()