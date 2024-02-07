"""
create-eventbridge-schedule uses details provided in a json file to create
rules in AWS EventBridge with the job-router Lambda as a target for invocation
"""
import json
import os
import boto3
import sys

client = boto3.client('events')
lambda_client = boto3.client('lambda')

environment = os.getenv('CMR_ENVIRONMENT').lower()
jobs_file = os.getenv('JOBS_FILE', '../job-details.json')

if len(sys.argv) < 2:
    print("Usage is 'python deploy_schedule.py job_name'")
    exit()

if environment==None:
    print("CMR_ENVIRONMENT variable needs to be set")
    exit()

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

    cron_expression = cron_expression.format(minutes, hours, day_of_month, month, day_of_week, year)
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

def make_input_json(job):
    """
    Pulls the target dictionary field from the job details to make
    an event for sending to Lambda on invocation
    """
    return json.dumps(job["target"])

with open(jobs_file, encoding="UTF-8") as json_file:
    jobs_map = json.load(json_file)

    job_name = sys.argv[1]
    if not job_name in jobs_map:
        print("Job details for " + job_name + " do not exist in " + jobs_file + " file")
        exit()

    lambda_details = lambda_client.get_function(
        FunctionName="job-router-"+environment
    )
    
    job_details = jobs_map[job_name]
    response = client.put_rule(
        Name=job_name,
        ScheduleExpression=make_schedule_expression(job_details),
        State='ENABLED'
    )

    response = client.put_targets(
        Rule=job_name,
        Targets=[
            {
                "Id" : "job-router-" + environment,
                "Arn" : lambda_details["Configuration"]["FunctionArn"],
                "Input" : make_input_json(job_details)
            }
        ]
    )
    print(job_name + " job event deployed")
