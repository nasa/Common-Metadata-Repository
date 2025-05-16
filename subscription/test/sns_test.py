import unittest
from unittest.mock import MagicMock, patch
import boto3
from botocore.exceptions import ClientError
from sns import Sns

class TestSns(unittest.TestCase):

    def setUp(self):
        self.mock_sns_resource = MagicMock()
        self.sns = Sns(self.mock_sns_resource)

    def test_create_topic_success(self):
        mock_topic = MagicMock()
        self.mock_sns_resource.create_topic.return_value = mock_topic
        
        result = self.sns.create_topic("test_topic")
        
        self.assertEqual(result, mock_topic)
        self.mock_sns_resource.create_topic.assert_called_once_with(Name="test_topic")

    def test_create_topic_client_error(self):
        self.mock_sns_resource.create_topic.side_effect = ClientError(
            {'Error': {'Code': 'TestException', 'Message': 'Test error message'}},
            'CreateTopic'
        )
        
        with self.assertRaises(ClientError):
            self.sns.create_topic("test_topic")

if __name__ == '__main__':
    unittest.main()
