"""
Test module for unit testing eventbridge_schdeule/deploy_schedule.py
"""
import unittest
from unittest.mock import patch, mock_open, Mock, MagicMock
import json
import os
from botocore.exceptions import ClientError
from eventbridge_schedule import deploy_schedule

test_job_data = {
    "CronSingleTargetJob" :
    {
        "target" : {
            "endpoint" : "cron/job/endpoint",
            "service" : "cool-service",
            "single-target" : True
        },
        "scheduling" : {
            "type" : "cron",
            "timing" : {
                "minutes" : 1,
                "hours" : 2,
                "day-of-month" : "*",
                "month" : "*",
                "day-of-week" : "?",
                "year" : "*"
            }
        }
    },
    "ScheduleSingleTargetJob" :
    {
        "target" : {
            "endpoint" : "schedule/job/endpoint",
            "service" : "cool-service",
            "single-target" : True
        },
        "scheduling" : {
            "type" : "interval",
            "timing" : {
                "minutes" : 35,
                "hours" : 1
            }
        }
    }
}

test_error_response = {
    'Error': {
        'Code': 'TestErrorException',
        'Message': 'Error tossed from test'
    },
    'ResponseMetadata': {
        'RequestId': '1234567890ABCDEF',
        'HostId': 'host ID data will appear here as a hash',
        'HTTPStatusCode': 400,
        'HTTPHeaders': {'header metadata key/values will appear here'},
        'RetryAttempts': 0
    }
}

class TestDeploySchedule(unittest.TestCase):
    """
    Unittest class
    """
    def test_make_cron_expression(self):
        """
        Test the make_cron_expression function creates an expression of the correct format
        """
        self.assertEqual(deploy_schedule.make_cron_expression(test_job_data["CronSingleTargetJob"]),
                         "cron(1 2 * * ? *)")

    def test_make_interval_expression(self):
        """
        Test the make_interval_expression function creates an expression of the correct format
        """
        self.assertEqual(deploy_schedule.make_interval_expression(test_job_data["ScheduleSingleTargetJob"]),
                         "rate(95 minutes)")

    def test_make_schedule_expressions(self):
        """
        Test the make_schedule function correcly creates both cron and schedule expressions
        """
        self.assertEqual(deploy_schedule.make_schedule_expression(test_job_data["CronSingleTargetJob"]),
                         "cron(1 2 * * ? *)")
        self.assertEqual(deploy_schedule.make_schedule_expression(test_job_data["ScheduleSingleTargetJob"]),
                         "rate(95 minutes)")

    @patch('builtins.open', new_callable=mock_open, read_data=json.dumps(test_job_data))
    @patch.dict(os.environ, {}, clear=True)
    def test_wrong_environment_variables(self, _mock_file):
        """
        Test that deploy_schedules fails out when needed environment variables aren't set
        """
        with self.assertRaises(SystemExit):
            deploy_schedule.get_environment()

    @patch('boto3.client')
    @patch('builtins.open', new_callable=mock_open, read_data=json.dumps(test_job_data))
    @patch.dict(os.environ, {"CMR_ENVIRONMENT": "test"})
    def test_get_function_client_error(self, _mock_file, mock_client):
        """
        Test that deploy_schedule fails out when client.get_function returns a ClientError
        """
        get_function_mock = Mock()
        get_function_mock.get_function.side_effect = ClientError(test_error_response, "get_function")

        mock_client.return_value = get_function_mock

        deploy_schedule.environment = "test"

        with self.assertRaises(SystemExit):
            deploy_schedule.get_lambda_function()

if __name__ == '__main__':
    unittest.main()
