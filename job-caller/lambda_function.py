import sys
import os
import boto3
import urllib3
import jmespath

client = boto3.client('ecs')
ssm_client = boto3.client('ssm')

service_port_map = {
    'metadata-db' : '3001',
    'ingest' : '3002',
    'search' : '3003',
    'indexer' : '3004',
    'bootstrap' : '3006',
    'virtual-product' : '3009',
    'access-control' : 3011
}

def handler(event, context):
    environment = os.getenv('CMR_ENVIRONMENT', 'local')
    cmr_url = os.getenv('CMR_LB_URL', 'localhost')
    service = event.get('service', 'bootstrap')
    job = event.get('job')
    target = event.get('target', 'single')

    print(event)

    if (cmr_url == 'localhost'):
        print("Running " + job + " job for " + service + " on local. URL: " + cmr_url + ':' + service_port_map[service] + '/' + job)
        response = urllib3.request("POST", cmr_url + ":" + service_port_map[service] + "/" + job)
    else:
        token = ssm_client.get_parameter(Name='/'+environment+'/'+service+'/CMR_ECHO_SYSTEM_TOKEN', WithDecryption=True)['Parameter']['Value']
        if (target == 'single'):
            # response = requests.post(cmr_url + '/' + service + '/' + job)
            print("Running " + job + " job for " + service + " on remote with URL: " + cmr_url + '/' + service + '/' + job)
            response = urllib3.request("POST", cmr_url + '/' + service + '/' + job, headers={"Authorization": token})
        else:
            response = client.list_tasks(
                cluster='cmr-service-'+environment,
                serviceName=service+'-'+environment
            )['taskArns']
            print(response)

            response2 = client.describe_tasks(
                cluster='cmr-service-'+environment,
                tasks=response
            )
            task_ips = jmespath.search("tasks[*].attachments[0].details[?name=='privateIPv4Address'].value", response2)
            task_ips = jmespath.search("[]", task_ips)

            for task in task_ips:
                print("Running " + job + " job for " + service + " on remote with URL: " + task + '/' + service + '/' + job)
                response = urllib3.request("POST", task + '/' + service + '/' + job, headers={"Authorization": token})
