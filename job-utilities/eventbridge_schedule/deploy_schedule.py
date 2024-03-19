"""
create-eventbridge-schedule uses details provided in a json file to create
rules in AWS EventBridge with the job-router Lambda as a target for invocation
"""
import json
import os
import sys
import boto3
from botocore.exceptions import ClientError

def make_cron_expression(job):
    """
    Takes the job details to construct a cron expression
    in the format cron(minutes hours day-of-the-month month day-of-the-week year)
    """
    cron_expression = 'cron({} {} {} {} {} {})'

    minutes = str(job["scheduling"]["timing"]["minutes"])
    hours = str(job["scheduling"]["timing"]["hours"])
    day_of_month = str(job["scheduling"]["timing"]["day-of-month"])
    month = str(job["scheduling"]["timing"]["month"])
    day_of_week = str(job["scheduling"]["timing"]["day-of-week"])
    year = str(job["scheduling"]["timing"]["year"])

    cron_expression = cron_expression.format(minutes, hours, day_of_month,
                                             month, day_of_week, year)
    return cron_expression

def make_interval_expression(job):
    """
    Takes the job details to construct an interval scheduling expression
    in the format rate(number minutes)
    """
    interval_expression = 'rate({} minutes)'

    hours = job["scheduling"]["timing"].get("hours", 0)
    minutes = job["scheduling"]["timing"]["minutes"]

    interval_expression = interval_expression.format(str(hours*60 + minutes))
    return interval_expression

def make_schedule_expression(job):
    """
    Determines if a cron or interval based scheduling expression needs
    to be made for the job and returns the result
    """
    if job["scheduling"]["type"] == "cron":
        return make_cron_expression(job)
    return make_interval_expression(job)

def deploy_schedule(job_name, jobs_file_name):
    """
    Uses the job details provided in the jobs_file to put rules
    into AWS EventBridge that invoke the job-router lambda
    for each given job in the jobs_file
    """
    environment = os.getenv('CMR_ENVIRONMENT')

    if environment is None:
        print("CMR_ENVIRONMENT variable needs to be set")
        sys.exit(1)

    with open(jobs_file_name, encoding="UTF-8") as json_file:
        jobs_map = json.load(json_file)

        if not job_name in jobs_map:
            print("Job details for " + job_name + " do not exist in "
                  + jobs_file_name + " file")
            sys.exit(1)

        lambda_client = boto3.client('lambda')
        try:
            lambda_details = lambda_client.get_function(
                FunctionName="job-router-"+environment
            )
        except ClientError as e:
            print("Error getting lambda function: " + e.response['Error']['Code'])
            sys.exit(1)

        job_details = jobs_map[job_name]

        client = boto3.client('events')
        try:
            create_rule_resp = client.put_rule(
                Name=job_name,
                ScheduleExpression=make_schedule_expression(job_details),
                State='ENABLED'
            )
        except ClientError as e:
            print("Error putting EventBridge rule: " + e.response["Error"]["Code"])
            sys.exit(1)

        try:
            lambda_client.add_permission(
                FunctionName="job-router-"+environment,
                StatementId="InvokeJobRouter",
                Action="lambda:InvokeFunction",
                Principal="events.amazonaws.com",
                SourceArn=create_rule_resp["RuleArn"]
            )
        except ClientError as e:
            print("Error adding permissions to lambda: " + e.response["Error"]["Code"])
            print("This does not mean the deployment will fail, it could just indicate the permission already exists")

        try:
            client.put_targets(
                Rule=job_name,
                Targets=[
                    {
                        "Id" : "job-router-" + environment,
                        "Arn" : lambda_details["Configuration"]["FunctionArn"],
                        "Input" : json.dumps(job_details["target"])
                    }
                ]
            )
        except ClientError as e:
            print("Error putting lambda target: " + e.response["Error"]["Code"])
            sys.exit(1)

        print(job_name + " job event deployed")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage is 'python deploy_schedule.py job_name [jobs_file]'")
        sys.exit(1)

    jobs_file = "../job-details.json" if len(sys.argv) < 3 else sys.argv[2]
    deploy_schedule(sys.argv[1], jobs_file)
