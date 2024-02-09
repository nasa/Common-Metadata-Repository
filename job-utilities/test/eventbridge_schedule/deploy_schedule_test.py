import unittest
import json

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

class TestDeploySchedule(unittest.TestCase):
    def test_make_cron_expression(self):
        self.assertEqual(deploy_schedule.make_cron_expression(test_job_data["CronSingleTargetJob"]), "cron(1 2 * * ? *)")
    
    def test_make_interval_expression(self):
        self.assertEqual(deploy_schedule.make_interval_expression(test_job_data["ScheduleSingleTargetJob"]), "rate(95 minutes)")

    def test_make_schedule_expressions(self):
        self.assertEqual(deploy_schedule.make_schedule_expression(test_job_data["CronSingleTargetJob"]), "cron(1 2 * * ? *)")
        self.assertEqual(deploy_schedule.make_schedule_expression(test_job_data["ScheduleSingleTargetJob"]), "rate(95 minutes)")

if __name__ == '__main__':
    unittest.main()