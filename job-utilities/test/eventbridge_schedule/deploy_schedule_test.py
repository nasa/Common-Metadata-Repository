import unittest
from unittest.mock import patch, mock_open, Mock, MagicMock
import json
import os

import boto3
from botocore.exceptions import ClientError
from botocore.stub import Stubber

import eventbridge_schedule.deploy_schedule as deploy_schedule

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
    def test_make_cron_expression(self):
        self.assertEqual(deploy_schedule.make_cron_expression(test_job_data["CronSingleTargetJob"]), "cron(1 2 * * ? *)")
    
    def test_make_interval_expression(self):
        self.assertEqual(deploy_schedule.make_interval_expression(test_job_data["ScheduleSingleTargetJob"]), "rate(95 minutes)")

    def test_make_schedule_expressions(self):
        self.assertEqual(deploy_schedule.make_schedule_expression(test_job_data["CronSingleTargetJob"]), "cron(1 2 * * ? *)")
        self.assertEqual(deploy_schedule.make_schedule_expression(test_job_data["ScheduleSingleTargetJob"]), "rate(95 minutes)")

    def client_mocks(client_type):
        mock = Mock()
        return mock

    @patch('boto3.client')
    @patch('builtins.open', new_callable=mock_open, read_data=json.dumps(test_job_data))
    def test_get_function_client_error(self, mock_file, mock_client):
        get_function_mock = Mock()
        get_function_mock.get_function.side_effect = ClientError(test_error_response, "get_function")

        mock_client.return_value = get_function_mock
        os.environ["CMR_ENVIRONMENT"] = "test"

        with self.assertRaises(SystemExit):
            deploy_schedule.deploy_schedule("CronSingleTargetJob", "TestFile")

    @patch('boto3.client')
    @patch('builtins.open', new_callable=mock_open, read_data=json.dumps(test_job_data))
    def test_put_rule_client_error(self, mock_file, mock_client):
        put_rule_mock = Mock()
        put_rule_mock.put_rule.side_effect = ClientError(test_error_response, "put_rule")

        mock_client.return_value = put_rule_mock
        os.environ["CMR_ENVIRONMENT"] = "test"

        with self.assertRaises(SystemExit):
            deploy_schedule.deploy_schedule("CronSingleTargetJob", "TestFile")

    @patch('boto3.client')
    @patch('builtins.open', new_callable=mock_open, read_data=json.dumps(test_job_data))
    def test_put_target_client_error(self, mock_file, mock_client):
        put_targets_mock = MagicMock()
        put_targets_mock.put_targets.side_effect = ClientError(test_error_response, "put_targets")

        mock_client.return_value = put_targets_mock
        os.environ["CMR_ENVIRONMENT"] = "test"

        with self.assertRaises(SystemExit):
            deploy_schedule.deploy_schedule("CronSingleTargetJob", "TestFile")
        

if __name__ == '__main__':
    unittest.main()