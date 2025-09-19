"""
create-eventbridge-schedule uses details provided in a json file to create
rules in AWS EventBridge with the job-router Lambda as a target for invocation
"""
import json
import os
import sys
import boto3
import argparse
from botocore.exceptions import ClientError

environment = None;
lambda_client = None;
lambda_arn = None;
event_client = None;

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

def create_event_targets(jobs_map):
    """
    Creates AWS EventBridge lambda targets so that when the EventBridge scheduler
    fires, the job router lambda is called to carry out the task, which calls a
    CMR API call to refresh a cache.
    """
    targets=[]

    # Go through each of the jobs and create a target for each one
    # that will call the job router lambda.
    for jobs_key in jobs_map:
        job_details = jobs_map[jobs_key]

        try:
            events_client.put_targets(
                Rule=jobs_key,
                Targets=[{"Id" : "job-router-" + environment,
                          "Arn" : lambda_arn,
                          "Input" : json.dumps(job_details["target"])}])
        except ClientError as e:
            print("Error putting lambda target: " + e.response["Error"]["Code"])

def add_lambda_permissions(rules):
    """
    Creates lambda permissions for each job so that the job router lambda can be
    invoked by AWS EventBridge rules.
    """
    for job_key in rules:
        rule = rules[job_key]
        try:
            lambda_client.add_permission(
                FunctionName="job-router-"+environment,
                StatementId="InvokeJobRouter" + "_" + job_key,
                Action="lambda:InvokeFunction",
                Principal="events.amazonaws.com",
                SourceArn=rule["RuleArn"])
        except ClientError as e:
            print("Error adding permissions to lambda: " + e.response["Error"]["Code"])
            print("This does not mean the deployment will fail, it could just indicate the permission already exists")

def create_scheduler_rules(jobs_map):
    """
    Creates AWS EventBridge scheduler rules to schedule all the refresh cache jobs.
    """
    rules = {}
    global events_client
    events_client = boto3.client('events')

    for job_key in jobs_map:
        try:
            create_rule_resp = events_client.put_rule(
                Name=job_key,
                ScheduleExpression=make_schedule_expression(jobs_map[job_key]),
                State='ENABLED')
            rules[job_key] = create_rule_resp
        except ClientError as e:
            print("Error putting EventBridge rule: " + e.response["Error"]["Code"])

    return rules

def get_lambda_function():
    """
    Gets the job router AWS lambda client and its details such as its arn.
    """
    global lambda_client
    lambda_client = boto3.client('lambda')
    try:
        lambda_details = lambda_client.get_function(
            FunctionName="job-router-"+environment
        )
        global lambda_arn
        lambda_arn = lambda_details["Configuration"]["FunctionArn"]
    except ClientError as e:
        print("Error getting lambda function: " + e.response['Error']['Code'])
        sys.exit(1)

def deploy_schedules(jobs_map):
    """
    Uses the job details provided in the jobs_file to put rules
    into AWS EventBridge that invoke the job-router lambda
    for each given job in the jobs_file
    """
    # First get the lambda function. This function sets the 
    # lambda_client and lambda_arn global variables
    get_lambda_function()

    # Then create the schedule rules for each job. This function
    # also sets the event_client global variable
    rules = create_scheduler_rules(jobs_map)

    # Then add the permissions for the event bridge scheduler to invoke
    # the job router lambda for each scheduled rule.
    add_lambda_permissions(rules)

    # Lastly add the job router lambda as a target for each schedule rule
    # so the scheduler can invoke the job router lambda when a schedule rule fires.
    create_event_targets(jobs_map)

    print("job events deployed")

def get_jobs_map(job_name, jobs_file_name):
    """
    Reads the file that holds the event bridge scheduler jobs.
    If a specific job name is passed in then just that job will be
    deployed. Otherwise all jobs will be deployed.
    """
    with open(jobs_file_name, encoding="UTF-8") as json_file:
        jobs_map = json.load(json_file)

        if not job_name:
            return jobs_map
        else:
            if not job_name in jobs_map:
                print("Job details for " + job_name + " do not exist in "
                      + jobs_file_name + " file")
                sys.exit(1)
            else:
                return {job_name: jobs_map[job_name]}

def get_args():
    """
    Parse the passed in arguments if any. Provide defaults values.
    """
    parser = argparse.ArgumentParser()
    parser.add_argument("--job_name", type=str, default=None, help="Name of the job in the job details file to deploy just 1 job. Do not use to deploy all jobs.")
    parser.add_argument("--jobs_file_name", type=str, default="../job-details.json",  help="Name of a specific jobs file to be deployed.")

    return parser.parse_args()

def get_environment():
    """
    Return the environment in which to deploy the event scheduler rules.
    """
    environment = os.getenv('CMR_ENVIRONMENT')

    if environment is None:
        print("CMR_ENVIRONMENT variable needs to be set")
        sys.exit(1)
    return environment

if __name__ == '__main__':
    """
    Deploy the event scheduler rules defined in a file. By default the
    ../jobs-details.json file is used. Then tie these rules to the job-router
    lambda which will fire when the schedule has been met. The lambda will call
    the defined CMR URL to complete the jobs task - normally this is to refresh
    a cache.  By default all jobs are deployed, but a job-name can be passed in
    to deploy just 1 job. Alternately, a job-details file name can also be passed
    in if a specific file is to be used.

    Usage:
    python3 deploy_schedule.py
    or
    python3 deploy_schedule.py --job_name <name of specific job to deploy>
    or
    python3 deploy_schedule.py --jobs_file_name <name of specific json file where jobs are stored>
    or
    python3 deploy_schedule.py --job_name <name of specific job to deploy> \
                               --jobs_file_name <name of specific json file where jobs are stored>
    """
    # Get the deployment environment from the CMR_ENVIRONMENT variable
    environment = get_environment()

    # Parse any command line arguments if any exist. Provide defaults.
    args = get_args()

    # Get the jobs from the json jobs definition file.
    jobs_map = get_jobs_map(args.job_name, args.jobs_file_name)

    # Deploy the schedules and tie them to the job router lambda.
    deploy_schedules(jobs_map)
