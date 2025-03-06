"""
Job-router Lambda function will take an event from
an AWS EventBridge rule and make a call to an endpoint
for running scheduled jobs. These endpoints could be through
a load balancer or directly to an ECS service
"""
import json
import os
import sys

import boto3
from urllib3 import request
import jmespath

def handler(event, _):
    """
    Entry point for Lambda function invocation.
    Some environment variables are required.
    These should be set on the deployed Lambda.
    CMR_ENVIRONMENT - Where the Lambda is deployed
    CMR_LB_NAME - The LB used for routing calls to CMR
    """
    environment = os.getenv('CMR_ENVIRONMENT')
    cmr_lb_name = os.getenv('CMR_LB_NAME')
    timeout = int(os.getenv('ROUTER_TIMEOUT', '30'))
    service = event.get('service', 'bootstrap')
    endpoint = event.get('endpoint')
    single_target = event.get('single-target', True)
    request_type = event.get('request-type', "GET")

    if environment is None:
        print("ERROR: CMR_ENVIRONMENT variable not set!")
    if cmr_lb_name is None:
        print("ERROR: CMR_LB_NAME variable not set!")
    #An extra check here so that if both variables are not set,
    #it can at least be reported at one time
    if environment is None or cmr_lb_name is None:
        sys.exit(1)

    if environment == 'local':
        json_file = open('service-ports.json', encoding="UTF-8")
        service_ports = json.load(json_file)

        token = 'mock-echo-system-token'
        headers = {"Authorization": token, "client-id": "cmr-job-router"}

        print("Sending to: " + "host.docker.internal:" + service_ports[service] + "/" + endpoint)
        try:
            response = request(request_type, "host.docker.internal:" + service_ports[service] + "/" + endpoint, \
                                       headers=headers, timeout=timeout)
            if response.status != 200:
                print("Error received sending " + request_type + " to " + "host.docker.internal:" + service_ports[service] + "/" + endpoint \
                        + ": " + str(response.status) + " reason: " + response.reason)
                sys.exit(-1)
        except Exception as e:
            print("Ran into an error!")
            print(e)
            sys.exit(-1)
        return

    client = boto3.client('ecs')
    ssm_client = boto3.client('ssm')
    elb_client = boto3.client('elbv2')

    cmr_url = elb_client.describe_load_balancers(Names=[cmr_lb_name])["LoadBalancers"][0]["DNSName"]

    token = ssm_client.get_parameter(Name='/'+environment+'/'+service+'/CMR_ECHO_SYSTEM_TOKEN', \
                                     WithDecryption=True)['Parameter']['Value']

    headers = {"Authorization": token, "client-id": "cmr-job-router"}

    if single_target:
        print("Running " + request_type + " on URL: " + cmr_url + '/' + service + '/' + endpoint)

        response = request(request_type, cmr_url + '/' + service + '/' + endpoint,
                                        headers=headers, timeout=timeout)
        if response.status != 200:
            print("Error received sending request to " + cmr_url + '/' + service + '/' + endpoint \
                  + ": " + str(response.status) + " reason: " + response.reason)
            sys.exit(-1)
    else:
        #Multi-target functionality is not fully implemented.
        #CMR-9688 has been made to finish this part out
        response = client.list_tasks(
            cluster='cmr-service-'+environment,
            serviceName=service+'-'+environment
        )['taskArns']

        response = client.describe_tasks(
            cluster='cmr-service-'+environment,
            tasks=response
        )
        task_ips = jmespath.search("tasks[*].attachments[0].details[?name=='privateIPv4Address'].value", response)
        task_ips = jmespath.search("[]", task_ips)

        for task in task_ips:
            print("Running POST on URL: " + task + '/' + service + '/' + endpoint)

            response = request(request_type, task + '/' + service + '/' + endpoint, \
                               headers=headers, timeout=timeout)
            if response.status != 200:
                print("Error received sending " + request_type + " to " + task + '/' + service + '/' + endpoint \
                      + ": " + str(response.status) + " reason: " + response.reason)
                sys.exit(-1)
