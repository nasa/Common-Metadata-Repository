"""
Job-router Lambda function will take an event from
an AWS EventBridge rule and make a call to an endpoint
for running scheduled jobs. These endpoints could be through
a load balancer or directly to an ECS service
"""
import os
import sys
import boto3
import urllib3
import jmespath

client = boto3.client('ecs')
ssm_client = boto3.client('ssm')

def handler(event, _):
    """
    Entry point for Lambda function invocation.
    Some environment variables are required.
    These should be set on the deployed Lambda.
    CMR_ENVIRONMENT - Where the Lambda is deployed
    CMR_LB_URL - The LB used for routing calls to CMR
    """
    environment = os.getenv('CMR_ENVIRONMENT')
    cmr_url = os.getenv('CMR_LB_URL')
    service = event.get('service', 'bootstrap')
    endpoint = event.get('endpoint')
    single_target = event.get('single-target', True)

    error_state = False

    if environment is None:
        print("ERROR: Environment variable not set!")
        error_state = True
    if cmr_url is None:
        print("ERROR: CMR_URL variable not set!")
        error_state = True
    if error_state:
        sys.exit()

    token_param_name = '/'+environment+'/'+service+'/CMR_ECHO_SYSTEM_TOKEN'
    token = ssm_client.get_parameter(Name=token_param_name,
                                        WithDecryption=True)['Parameter']['Value']

    if single_target:
        print("Running POST on URL: " + cmr_url + '/' + service + '/' + endpoint)

        response = urllib3.request("POST", cmr_url + '/' + service + '/' + endpoint,
                                    headers={"Authorization": token},
                                    timeout=urllib3.Timeout(15))
    else:
        #Multi-target functionality is not fully implemented.
        #CMR-9688 has been made to finish this part out
        response = client.list_tasks(
            cluster='cmr-service-'+environment,
            serviceName=service+'-'+environment
        )['taskArns']

        response2 = client.describe_tasks(
            cluster='cmr-service-'+environment,
            tasks=response
        )
        task_ips = jmespath.search("tasks[*].attachments[0].details[?name=='privateIPv4Address'].value", response2)
        task_ips = jmespath.search("[]", task_ips)

        for task in task_ips:
            print("Running POST on URL: " + task + '/' + service + '/' + endpoint)

            response = urllib3.request("POST", task + '/' + service + '/' + endpoint,
                                        headers={"Authorization": token},
                                        timeout=urllib3.Timeout(15))
