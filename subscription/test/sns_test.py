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

    @patch('json.loads')
    def test_publish_message_with_attributes(self, mock_json_loads):
        mock_topic = MagicMock()
        mock_message = {
            "Body": '{"Subject": "Test Subject", "MessageAttributes": {"attr1": {"Value": "value1"}}, "Message": "Test Message"}'
        }
        mock_json_loads.return_value = {
            "Subject": "Test Subject",
            "MessageAttributes": {"attr1": {"Value": "value1"}},
            "Message": "Test Message"
        }
        
        Sns.publish_message(mock_topic, mock_message)
        
        mock_topic.publish.assert_called_once_with(
            Subject="Test Subject",
            Message="Test Message",
            MessageAttributes={"attr1": {"DataType": "String", "StringValue": "value1"}}
        )

    @patch('json.loads')
    def test_publish_message_without_attributes(self, mock_json_loads):
        mock_topic = MagicMock()
        mock_message = {
            "Body": '{"Subject": "Test Subject", "MessageAttributes": {}, "Message": "Test Message"}'
        }
        mock_json_loads.return_value = {
            "Subject": "Test Subject",
            "MessageAttributes": {},
            "Message": "Test Message"
        }
        
        Sns.publish_message(mock_topic, mock_message)
        
        mock_topic.publish.assert_called_once_with(
            Subject="Test Subject",
            Message="Test Message"
        )

    @patch('json.loads')
    def test_publish_message_client_error(self, mock_json_loads):
        mock_topic = MagicMock()
        mock_message = {
            "Body": '{"Subject": "Test Subject", "MessageAttributes": {}, "Message": "Test Message"}'
        }
        mock_json_loads.return_value = {
            "Subject": "Test Subject",
            "MessageAttributes": {},
            "Message": "Test Message"
        }
        mock_topic.publish.side_effect = ClientError(
            {'Error': {'Code': 'TestException', 'Message': 'Test error message'}},
            'Publish'
        )
        
        with self.assertRaises(ClientError):
            Sns.publish_message(mock_topic, mock_message)

if __name__ == '__main__':
    unittest.main()
