import unittest
from unittest.mock import patch, MagicMock
import boto3
from botocore.exceptions import ClientError
from subscription_worker import (receive_message, delete_message, delete_messages, process_messages, poll_queue, app)

class TestSubscriptionWorker(unittest.TestCase):

    @patch('boto3.client')
    def test_receive_message(self, mock_boto3_client):
        mock_sqs = MagicMock()
        mock_boto3_client.return_value = mock_sqs
        mock_sqs.receive_message.return_value = {'Messages': [{'MessageId': '1'}]}

        result = receive_message(mock_sqs, 'test-queue-url')
        
        mock_sqs.receive_message.assert_called_once_with(
            QueueUrl='test-queue-url',
            MaxNumberOfMessages=1,
            WaitTimeSeconds=10
        )
        self.assertEqual(result, {'Messages': [{'MessageId': '1'}]})

    @patch('boto3.client')
    def test_delete_message(self, mock_boto3_client):
        mock_sqs = MagicMock()
        mock_boto3_client.return_value = mock_sqs

        delete_message(mock_sqs, 'test-queue-url', 'receipt-handle')
        
        mock_sqs.delete_message.assert_called_once_with(
            QueueUrl='test-queue-url',
            ReceiptHandle='receipt-handle'
        )

    @patch('boto3.client')
    def test_delete_messages(self, mock_boto3_client):
        mock_sqs = MagicMock()
        mock_boto3_client.return_value = mock_sqs
        messages = {'Messages': [{'ReceiptHandle': 'receipt1'}, {'ReceiptHandle': 'receipt2'}]}

        delete_messages(mock_sqs, 'test-queue-url', messages)
        
        self.assertEqual(mock_sqs.delete_message.call_count, 2)
        mock_sqs.delete_message.assert_any_call(QueueUrl='test-queue-url', ReceiptHandle='receipt1')
        mock_sqs.delete_message.assert_any_call(QueueUrl='test-queue-url', ReceiptHandle='receipt2')

    @patch('subscription_worker.Sns')
    @patch('subscription_worker.AccessControl')
    def test_process_messages(self, mock_access_control, mock_sns):
        mock_sns_instance = MagicMock()
        mock_sns.return_value = mock_sns_instance
        mock_access_control_instance = MagicMock()
        mock_access_control.return_value = mock_access_control_instance

        messages = {'Messages': [{'Body': 'test message'}]}
        process_messages(mock_sns_instance, 'test-topic', messages, mock_access_control_instance)
        
        mock_sns_instance.publish_message.assert_called_once_with('test-topic', {'Body': 'test message'})

if __name__ == '__main__':
    unittest.main()
