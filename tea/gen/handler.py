"""
AWS lambda functions for generating a TEA configuration.
"""

import logging
import os
import json
import boto3

import gen.create_tea_config as tea

#pylint: disable=W0613 # AWS requires event and context, but these are not always used

# ******************************************************************************
#mark - Utility functions

def aws_return_message(status, body):
    """ build a dictionary which AWS Lambda will accept as a return value """
    return {"statusCode": status, "body": json.dumps(body, indent=1)}

def parameter_read(pname, default_value=''):
    """
    Try to read a parameter first from SSM, if not there, then try the os environment
    """
    #pylint: disable=W0703 # no, fall back to os in all cases
    try:
        ssm = boto3.client('ssm')
        param = ssm.get_parameter(Name=pname.upper(), WithDecryption=True)
        return param['Parameter']['Value']
    except Exception:
        return os.environ.get(pname.upper(), default_value)
    return default_value

def capability(event, endpoint, name, description):
    """
    A helper function to generate one capability line for the capabilities()
    """
    path = event['path']
    prefix = path[0:path.rfind('/')]
    return {'name': name, 'path': prefix+endpoint, 'description': description}

# ******************************************************************************
#mark - AWS Lambda functions

def capabilities(event, context):
    """ Return a static output stating the calls supported by this package """
    body = {
        'urls': [
            capability(event, '/', 'capabilities', 'what can be done here'),
            capability(event, '/capabilities', 'capabilities', 'what can be done here'),
            capability(event, '/status', 'Simple status', 'status'),
            capability(event, '/<provider>', 'Generate', 'Generate a TEA config for provider'),
        ]
    }
    return aws_return_message(200, body)

def debug(event, context):
    """ Return debug information about AWS in general """
    body = {
        'arn': f"Lambda function ARN:{context.invoked_function_arn}",
        'log_stream' : f"CloudWatch log stream name: {context.log_stream_name}",
        'group' : f"CloudWatch log group name:{context.log_group_name}",
        'request-id': f"Lambda Request ID:{context.aws_request_id}",
        'memory-limit': f"Lambda function memory limits in MB:{context.memory_limit_in_mb}",
        'remaining-time': f"Lambda time remaining in MS:{context.get_remaining_time_in_millis()}",
        'event': event,
        'tea_config_cmr': parameter_read('aws_tea_config_cmr',
            default_value='https://cmr.earthdata.nasa.gov'),
    }

    for env, value in os.environ.items():
        if env.startswith('AWS_'):
            body[env] = value
    return aws_return_message(200, body)

def teapot(event, context):
    """
    Provide an enpoint for testing service availability and for complicance with
    RFC 7168
    """
    return aws_return_message(418, "I'm a teapot")

def generate_tea_config(event, context):
    """
    Lambda function to return a TEA configuration. Requires that event contain
    the following:
    * CMR must be configured with an env variable
    * Path Parameter named 'id' with CMR provider name
    * HTTP Header named 'Cmr-Token' with a CMR compatible token
    """
    cmr_config = parameter_read('aws_tea_config_cmr',
      default_value='https://cmr.earthdata.nasa.gov')

    provider = event['pathParameters']['id']
    token = event['headers']['Cmr-Token']

    env = {'cmr-url':cmr_config, 'logging-level': 'info'}

    processor = tea.CreateTeaConfig()
    result = processor.create_tea_config(env, provider, token)

    #result = {'env': env, 'provider': provider, 'token': token}

    return aws_return_message(200, result)

# ******************************************************************************
#mark - command line functions

def main():
    """ Used to test functions locally """
    if len(logging.getLogger().handlers)>0:
        logging.getLogger().setLevel(logging.INFO)
    else:
        logging.basicConfig(filename='script.log',
          format='%(asctime)s %(message)s',
          #encoding='utf-8',
          level=logging.DEBUG)

    provider = input('Enter provider: (POCLOUD)')
    if provider is None:
        provider = 'POCLOUD'

    which_cmr = input('Enter env such as sit, uat or prod (uat): ')
    if which_cmr is None:
        which_cmr = 'uat'

    token = input('Enter EDL token: ')

    cmrs = {'sit':'https://cmr.sit.earthdata.nasa.gov',
        'uat':'https://cmr.uat.earthdata.nasa.gov',
        'prod':'https://cmr.earthdata.nasa.gov'}

    env = {'cmr-url': cmrs[which_cmr],
        'logging_level': 'INFO'
    }

    processor = tea.CreateTeaConfig()
    result = processor.create_tea_config(env, provider, token)
    print (result)

if __name__ == "__main__":
    main()
