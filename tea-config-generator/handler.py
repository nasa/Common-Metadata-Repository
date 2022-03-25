"""
AWS lambda functions for generating a TEA configuration.
"""

import os
import json
import logging
import datetime
import boto3

import tea.gen.create_tea_config as tea

#pylint: disable=W0613 # AWS requires event and context, but these are not always used

# ******************************************************************************
#mark - Utility functions

def lowercase_dictionary(dirty_dictionary):
    """
    Standardize the keys in a header to lower case to reduce the number of lookup
    cases that need to be supported. Assumes that there are no duplicates, if
    there are, the last one is saved.
    """
    return dict((key.lower(), value) for key,value in dirty_dictionary.items())

def finish_timer(start):
    """ Calculate and format the number of seconds from start to 'now'. """
    delta = datetime.datetime.now() - start
    sec = delta.total_seconds()
    return f"{sec:.6f}"

def pretty_indent(event):
    """ Look for pretty in both the header and query parameters """
    indent = None # none means do not pretty print, a number is the tab count
    if event:
        pretty = 'false'

        # look first at query
        query_params = event.get('queryStringParameters')
        if query_params:
            pretty = query_params.get('pretty', "false")

        # next try the header
        if pretty=='false' and 'headers' in event:
            # standardize the keys to lowercase
            headers = lowercase_dictionary(event['headers'])
            pretty = headers.get('cmr-pretty', 'false')

        # set the output
        if pretty.lower()=="true":
            indent=1
    return indent

def read_file(file_name):
    """
    Read version file content written by the CI/CD system, return None if it
    does not exist
    """
    if os.path.exists(file_name):
        with open(file_name, encoding='utf-8') as out_file:
            return out_file.read()
    return None

def load_version():
    """ Load version information from a file. This file is written by CI/CD """
    ver = read_file('ver.txt')
    if ver is not None:
        return json.loads(ver)
    return None

def append_version(data:dict=None):
    """ Append CI/CD version information to a dictionary if it exists. """
    if data is not None:
        ver = load_version()
        if ver is not None:
            data['application'] = ver

def aws_return_message(event, status, body, headers=None, start=None):
    """ build a dictionary which AWS Lambda will accept as a return value """
    indent = pretty_indent(event)

    ret = {"statusCode": status,
        "body": json.dumps(body, indent=indent),
        'headers': {}}
    if start is not None:
        ret['headers']['cmr-took'] = finish_timer(start)
    if headers is not None:
        for header in headers:
            ret['headers'][header] = headers[header]
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

def init_logging():
    """
    Initialize the logging system using the logging level provided by the calling
    'environment' and return a logger
    """
    level = parameter_read('AWS_TEA_CONFIG_LOG_LEVEL', default_value='INFO')
    logging.basicConfig(format="%(name)s - %(module)s - %(message)s",level=level)
    logger = logging.getLogger()
    logger.setLevel(level)
    return logger

# ******************************************************************************
#mark - AWS Lambda functions

def debug(event, context):
    """ Return debug information about AWS in general """

    logger = init_logging()
    logger.info("Debug have been loaded")

    start = datetime.datetime.now()
    body = {
        'context': context.get_remaining_time_in_millis(),
        'event': event,
        'clean-header': lowercase_dictionary(event['headers']),
        'tea_config_cmr': parameter_read('AWS_TEA_CONFIG_CMR',
            default_value='https://cmr.earthdata.nasa.gov'),
        'tea_config_log_level': parameter_read('AWS_TEA_CONFIG_LOG_LEVEL',
            default_value='INFO'),
    }

    for env, value in os.environ.items():
        if env.startswith('AWS_'):
            if env in ['AWS_SESSION_TOKEN', 'AWS_SECRET_ACCESS_KEY', 'AWS_ACCESS_KEY_ID']:
                body[env] = '~redacted~'
            else:
                body[env] = value
    append_version(body)

    return aws_return_message(event, 200, body, start=start)

def health(event, context):
    """
    Provide an endpoint for testing service availability and for complicance with
    RFC 7168
    """
    logger = init_logging()
    logger.debug("health check has been requested")

    return aws_return_message(event,
        418,
        "I'm a teapot",
        headers={'content-type': 'text/plain'},
        start=datetime.datetime.now())

def generate_tea_config(event, context):
    """
    Lambda function to return a TEA configuration. Requires that event contain
    the following:
    * CMR must be configured with an env variable
    * Path Parameter named 'id' with CMR provider name
    * HTTP Header named 'Authorization' with a CMR compatible token
    """
    logger = init_logging()
    logger.debug("generate tea config started")

    start = datetime.datetime.now()

    provider = None
    if 'pathParameters' in event:
        provider = event['pathParameters'].get('id')
    elif 'path' in event:
        parts = event['path'].split('/', -1)
        if 1<len(parts) and parts[-2] == 'provider':
            provider = parts[-1]
    if provider is None:
        provider = event['queryStringParameters'].get('id')

    if provider is None or len(provider)<1:
        return aws_return_message(event,
            400,
            "Provider is required",
            headers={'content-type': 'text/plain'},
            start=start)

    headers = lowercase_dictionary(event['headers'])
    token = headers.get('authorization')
    if token is None or len(token)<1:
        return aws_return_message(event,
            400,
            "Token is required",
            headers={'content-type': 'text/plain'},
            start=start)

    env = {}
    env['cmr-url'] = parameter_read('AWS_TEA_CONFIG_CMR',
      default_value='https://cmr.earthdata.nasa.gov')
    env['logging-level'] = parameter_read('AWS_TEA_CONFIG_LOG_LEVEL',
      default_value='info')
    if pretty_indent(event) is None:
        env['pretty-print'] = False
    else:
        env['pretty-print'] = True

    processor = tea.CreateTeaConfig(env)
    result = processor.create_tea_config(env, provider, token)
    result['headers'] = {'cmr-took': finish_timer(start),
        'content-type': 'text/yaml',
        'cmr-url': env['cmr-url'],
        'cmr-auth': f'{token[0:20]}...',
        'cmr-provider': provider}

    return result
