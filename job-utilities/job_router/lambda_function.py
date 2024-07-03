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

def handler(event, _):
    """
    Entry point for Lambda function invocation.
    Some environment variables are required.
    These should be set on the deployed Lambda.
    CMR_ENVIRONMENT - Where the Lambda is deployed
    CMR_LB_URL - The LB used for routing calls to CMR
    """
    environment = os.getenv('CMR_ENVIRONMENT')
    cmr_lb_name = os.getenv('CMR_LB_NAME')
    service = event.get('service', 'bootstrap')
    endpoint = event.get('endpoint')
    single_target = event.get('single-target', True)
    request_type = event.get('request-type', "GET")

    error_state = False

    if environment is None:
        print("ERROR: Environment variable not set!")
        error_state = True
    if cmr_lb_name is None:
        print("ERROR: CMR_LB_NAME variable not set!")
        error_state = True
    if error_state:
        sys.exit(1)

    client = boto3.client('ecs')
    ssm_client = boto3.client('ssm')
    elb_client = boto3.client('elbv2')

    cmr_url = elb_client.describe_load_balancers(Names=[cmr_lb_name])[0]["DNSName"]

    token_param_name = '/'+environment+'/'+service+'/CMR_ECHO_SYSTEM_TOKEN'
    token = ssm_client.get_parameter(Name=token_param_name, WithDecryption=True)['Parameter']['Value']

    pool_manager = urllib3.PoolManager(headers={"Authorization": token}, timeout=urllib3.Timeout(15))

    if single_target:
        print("Running " + request_type + " on URL: " + cmr_url + '/' + service + '/' + endpoint)

        response = pool_manager.request(request_type, cmr_url + '/' + service + '/' + endpoint)
        if response.status != 200:
            print("Error received sending request to " + cmr_url + '/' + service + '/' + endpoint + ": " + response.status + " reason: " + response.reason)
            exit(-1)
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

            response = pool_manager.request(request_type, task + '/' + service + '/' + endpoint)
            if response.status != 200:
                print("Error received sending " + request_type + " to " + task + '/' + service + '/' + endpoint + ": " + response.status + " reason: " + response.reason)
                exit(-1)
