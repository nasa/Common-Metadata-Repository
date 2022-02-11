"""
AWS lambda functions for generating a TEA configuration.
"""

import logging
import os
import json
import datetime
import boto3

import tea.gen.create_tea_config as tea
#import tea.gen.create_tea_config as tea


#pylint: disable=W0613 # AWS requires event and context, but these are not always used

# ******************************************************************************
#mark - Utility functions

def finish_timer(start):
    """ Calculate and format the number of seconds from start to 'now'. """
    delta = datetime.datetime.now() - start
    sec = delta.total_seconds()
    return f"{sec:.6f}"

def pretty_indent(event):
    # look for pretty in both the header and query parameters
    indent = None # none means do not pretty print, a number is the tab count
    pretty = 'false'
    query_params = event.get('queryStringParameters')
    if query_params:
        pretty = query_params.get('pretty', "false")
    if pretty=='false' and 'Cmr-Pretty' in event['headers']:
        pretty = event['headers']['Cmr-Pretty']
    if pretty.lower()=="true":
        indent=1
    return indent

def aws_return_message(event, status, body, start=None):
    """ build a dictionary which AWS Lambda will accept as a return value """
    indent = pretty_indent(event)

    ret = {"statusCode": status, "body": json.dumps(body, indent=indent)}
    if start is not None:
        if 'headers' not in ret:
            ret['headers'] = {}
        ret['headers']['cmr-took'] = finish_timer(start)
    return ret

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
    start = datetime.datetime.now()
    body = {
        'urls': [
            capability(event, '/', 'Root', 'Alias for capabilities'),
            capability(event, '/capabilities', 'Capabilities',
                'Show which endpoints exist on this service'),
            capability(event, '/status', 'Status', 'Returns service status'),
            capability(event, '/provider/<provider>', 'Generate',
                'Generate a TEA config for provider'),
        ]
    }
    return aws_return_message(event, 200, body, start)

def debug(event, context):
    """ Return debug information about AWS in general """
    start = datetime.datetime.now()
    body = {
        'context': context.get_remaining_time_in_millis(),
        'event': event,
        'tea_config_cmr': parameter_read('AWS_TEA_CONFIG_CMR',
            default_value='https://cmr.earthdata.nasa.gov'),
        'tea_config_log_level': parameter_read('AWS_TEA_CONFIG_LOG_LEVEL',
            default_value='INFO'),
    }

    for env, value in os.environ.items():
        if env.startswith('AWS_'):
            body[env] = value
    return aws_return_message(event, 200, body, start)

def health(event, context):
    """
    Provide an endpoint for testing service availability and for complicance with
    RFC 7168
    """
    return aws_return_message(event, 418, "I'm a teapot", datetime.datetime.now())


def generate_tea_config(event, context):
    """
    Lambda function to return a TEA configuration. Requires that event contain
    the following:
    * CMR must be configured with an env variable
    * Path Parameter named 'id' with CMR provider name
    * HTTP Header named 'Cmr-Token' with a CMR compatible token
    """
    start = datetime.datetime.now()

    provider = event['pathParameters'].get('id')
    if provider is None or len(provider)<1:
        return aws_return_message(event, 400, "Provider is required", start)
    token = event['headers'].get('Cmr-Token')
    if token is None or len(token)<1:
        return aws_return_message(event, 400, "Token is required", start)

    env = {}
    env['cmr-url'] = cmr_config = parameter_read('AWS_TEA_CONFIG_CMR',
      default_value='https://cmr.earthdata.nasa.gov')
    env['logging-level'] = parameter_read('AWS_TEA_CONFIG_LOG_LEVEL',
      default_value='info')
    if pretty_indent(event) is None:
        env['pretty-print'] = False
    else:
        env['pretty-print'] = True

    processor = tea.CreateTeaConfig()
    result = processor.create_tea_config(env, provider, token)
    result['headers'] = {'cmr-took': finish_timer(start)}

    return result

