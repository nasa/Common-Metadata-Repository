import unittest
from unittest.mock import patch, MagicMock
import json
import requests
from notification_lambda import handler, process_message, send_message

class TestNotificationHandler(unittest.TestCase):

    @patch('notification_lambda.process_message')
    def test_handler(self, mock_process_message):
        event = {
            'Records': [
                {'some': 'data1'},
                {'some': 'data2'}
            ]
        }
        context = {}
        
        handler(event, context)
        
        self.assertEqual(mock_process_message.call_count, 2)
        mock_process_message.assert_any_call({'some': 'data1'})
        mock_process_message.assert_any_call({'some': 'data2'})

    @patch('notification_lambda.send_message')
    def test_process_message(self, mock_send_message):
        record = {
            'Sns': {
                'MessageId': '12345',
                'MessageAttributes': {
                    'endpoint': {
                        'Value': 'http://example.com'
                    }
                }
            }
        }
        
        process_message(record)
        
        mock_send_message.assert_called_once_with('http://example.com', record['Sns'])

    @patch('notification_lambda.send_message')
    def test_process_message_exception(self, mock_send_message):
        record = {
            'Sns': {
                'MessageId': '12345',
                'MessageAttributes': {}  # Missing 'endpoint' to cause an exception
            }
        }
        
        with self.assertRaises(Exception):
            process_message(record)

    @patch('requests.post')
    def test_send_message_success(self, mock_post):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_post.return_value = mock_response

        url = 'http://example.com'
        message = {'MessageId': '12345', 'some': 'data'}

        send_message(url, message)

        mock_post.assert_called_once_with(url, headers={'Content-Type': 'application/json'}, json=message)

    @patch('requests.post')
    def test_send_message_failure(self, mock_post):
        mock_response = MagicMock()
        mock_response.status_code = 400
        mock_response.text = 'Bad Request'
        mock_post.return_value = mock_response

        url = 'http://example.com'
        message = {'MessageId': '12345', 'some': 'data'}

        send_message(url, message)

        mock_post.assert_called_once_with(url, headers={'Content-Type': 'application/json'}, json=message)

    @patch('requests.post')
    def test_send_message_exception(self, mock_post):
        mock_post.side_effect = requests.exceptions.RequestException('Network error')

        url = 'http://example.com'
        message = {'MessageId': '12345', 'some': 'data'}

        send_message(url, message)

        mock_post.assert_called_once_with(url, headers={'Content-Type': 'application/json'}, json=message)

if __name__ == '__main__':
    unittest.main()

