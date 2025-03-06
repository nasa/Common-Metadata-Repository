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
import urllib3
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

    if not environment:
        print("ERROR: CMR_ENVIRONMENT variable not set!")
    if not os.getenv("CMR_LB_NAME"):
        print("ERROR: CMR_LB_NAME variable not set!")
    if environment is None or os.getenv("CMR_LB_NAME") is None:
        sys.exit(1)

    if environment == 'local':
        route_local(event=event)
    else:
        route(environment=environment, event=event)

def send_request(request_type, token, url):
    """
    Sends the request of given type with given token
    to given url
    """
    timeout = int(os.getenv('ROUTER_TIMEOUT', '300'))

    pool_manager = urllib3.PoolManager(headers={"Authorization": token, \
                                                "Client-Id": "cmr-job-router"}, \
                                    timeout=urllib3.Timeout(timeout))

    response = pool_manager.request(request_type, url)
    if response.status != 200:
        print(f"Error received sending {request_type} to {url}: " \
                + f"{str(response.status)} reason: {response.reason}")
        sys.exit(-1)

def route_local(event):
    """
    Handles the routing for a local request
    """
    service = event.get('service', 'bootstrap')
    endpoint = event.get('endpoint')
    request_type = event.get('request-type', "GET")

    with open('service-ports.json', encoding="UTF-8") as json_file:
        service_ports = json.load(json_file)

        token = 'mock-echo-system-token'

        print(f"Sending to: host.docker.internal:{service_ports[service]}/{endpoint}")
        try:
            send_request(request_type=request_type,
                            token=token,
                            url=f"host.docker.internal:{service_ports[service]}/{endpoint}")
        except Exception as e:
            print("Ran into an error!")
            print(e)
            sys.exit(-1)

def route(environment, event):
    """
    Handles routing for single target and multi target requests
    on a deployed environment
    """
    host = os.getenv('CMR_LB_NAME')
    service = event.get('service', 'bootstrap')
    endpoint = event.get('endpoint')
    single_target = event.get('single-target', True)
    request_type = event.get('request-type', "GET")

    client = boto3.client('ecs')
    ssm_client = boto3.client('ssm')
    elb_client = boto3.client('elbv2')

    cmr_url = elb_client.describe_load_balancers(Names=[host])["LoadBalancers"][0]["DNSName"]

    token = ssm_client.get_parameter(Name=f"/{environment}/{service}/CMR_ECHO_SYSTEM_TOKEN", \
                                    WithDecryption=True)['Parameter']['Value']

    if single_target:
        print(f"Running {request_type} on URL: {cmr_url}/{service}/{endpoint}")

        send_request(request_type=request_type,
                    token=token,
                    url=f"{cmr_url}/{service}/{endpoint}")
    else:
        #Multi-target functionality is not fully implemented.
        #CMR-9688 has been made to finish this part out
        response = client.list_tasks(
            cluster=f"cmr-service-{environment}",
            serviceName=f"{service}-{environment}"
        )['taskArns']

        response = client.describe_tasks(
            cluster=f"cmr-service-{environment}",
            tasks=response
        )
        task_ips = jmespath.search("tasks[*].attachments[0].details[?name=='privateIPv4Address'].value",\
                                    response)
        task_ips = jmespath.search("[]", task_ips)

        for task in task_ips:
            print(f"Running POST on URL: {task}/{service}/{endpoint}")

            send_request(request_type=request_type,
                        token=token,
                        url=f"{task}/{service}/{endpoint}")
