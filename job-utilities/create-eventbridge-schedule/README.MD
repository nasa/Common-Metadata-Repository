# create-eventbridge-schedule

Python application to deploy rules to eventbridge for job scheduling

## Running

As a prerequisite, the job-router lambda needs to be deployed and available as job-router-ENVIRONMENT_NAME.
Set the following environment variables:
CMR_ENVIRONMENT - Name of the environment being deployed to (i.e., sit, uat, prod)
AWS_ACCESS_KEY_ID - Needed for boto3 to communicate with AWS
AWS_SECRET_ACCESS_KEY - Needed for boto3 to communicate with AWS

Run the deploy_schedule.py program.