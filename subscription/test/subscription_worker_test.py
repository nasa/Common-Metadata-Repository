import json
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
    @patch('subscription_worker.Search')
    def test_process_messages(self, mock_access_control, mock_sns, mock_search):
        mock_sns_instance = MagicMock()
        mock_sns.return_value = mock_sns_instance
        mock_access_control_instance = MagicMock()
        mock_access_control.return_value = mock_access_control_instance
        mock_search_instance = MagicMock()
        mock_search.return_value = mock_search_instance

        mock_access_control_instance.has_read_permission.return_value = True
        mock_search_instance.process_message.return_value = '{"concept-id": "G1200484365-PROV", "granule-ur": "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01", "producer-granule-id": "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01.nc", "location": "http://localhost:3003/concepts/G1200484365-PROV/39"}'

        messages = {
            'Messages': [{
                'Body': json.dumps({
                    'Type': 'Notification',
                    'MessageId': 'dfb70dfe-6f63-5cfc-9a5f-6dc731b504de',
                    'TopicArn': 'arn:name',
                    'Subject': 'Update Notification',
                    'Message': '{"concept-id": "G1200484365-PROV", "granule-ur": "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01", "producer-granule-id": "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01.nc", "location": "http://localhost:3003/concepts/G1200484365-PROV/39"}',
                    'Timestamp': '2025-02-26T18:25:26.951Z',
                    'SignatureVersion': '1',
                    'Signature': 'HIQ==',
                    'SigningCertURL': 'https://sns.region.amazonaws.com/SNS-9.pem',
                    'UnsubscribeURL': 'https://sns.region.amazonaws.com/?Ac',
                    'MessageAttributes': {
                        'mode': {'Type': 'String', 'Value': 'Update'},
                        'collection-concept-id': {'Type': 'String', 'Value': 'C1200484363-PROV'},
                        'endpoint': {'Type': 'String', 'Value': 'http://notification/tester'},
                        'subscriber': {'Type': 'String', 'Value': 'user1_test'},
                        'endpoint-type': {'Type': 'String', 'Value': 'url'}
                    }
                })
            }]
        }

        process_messages(mock_sns_instance, 'test-topic', messages, mock_access_control_instance, mock_search_instance)

        # Check if has_read_permission was called with correct arguments
        mock_access_control_instance.has_read_permission.assert_called_once_with('user1_test', 'C1200484363-PROV')
        
        mock_sns_instance.publish_message.assert_called_once_with('test-topic', messages['Messages'][0])

        body = messages['Messages'][0]['Body']
        message = body['Message']

        mock_search_instance.process_message.assert_called_once_with(message)

if __name__ == '__main__':
    unittest.main()
