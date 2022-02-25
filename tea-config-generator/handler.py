"""
AWS lambda functions for generating a TEA configuration.
"""

#import logging
import os
import json
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
            if 'cmr-pretty' in headers:
                pretty = headers['cmr-pretty']

        # set the output
        if pretty.lower()=="true":
            indent=1
    return indent

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
    #if 'content-type' not in ret['headers']:
    #    ret['headers']['content-type'] = 'application/json'
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

def capability(event, endpoint, name, description, others=None):
    """
    A helper function to generate one capability line for the capabilities()
    """
    path = event['path']
    named_path_element = path.rfind('/capabilities')
    if named_path_element>0:
        prefix = path[0:named_path_element]
    else:
        prefix = path
    ret = {'name': name, 'path': prefix+endpoint, 'description': description}

    if others is not None:
        for item in others:
            if others[item] is not None:
                ret[item] = others[item]
    return ret

def header_line(name, description, head_type=None, values=None):
    """
    A helper function to generate one header line for the capabilities()
    """
    ret = {'name': name, 'description': description}
    if head_type is None:
        ret['type'] = 'string'
    else:
        ret['type'] = head_type
    if values is not None:
        ret['values'] = values
    return ret

# ******************************************************************************
#mark - AWS Lambda functions

def capabilities(event, context):
    """ Return a static output stating the calls supported by this package """
    start = datetime.datetime.now()

    h_pretty = header_line('Cmr-Pretty', 'format output with new lines',
        'string', ['true', 'false'])
    h_took = header_line('Cmr-Took', 'number of seconds used to process request', 'real')
    h_token = header_line('Authorization', 'CMR Compatable access token')
    h_type_json = header_line('content-type', 'content mime-type', None, 'application/json')
    h_type_text = header_line('content-type', 'content mime-type', None, 'text/plain')
    h_type_yaml = header_line('content-type', 'content mime-type', None, 'text/yaml')

    # optional return values
    optionals = lambda r,i,o : {'headers-in':i,'headers-out':o,'response':r}

    body = {
        'urls': [
            capability(event,
                '/',
                'Root',
                'Alias for the Capabilities'),
            capability(event,
                '/capabilities',
                'Capabilities',
                'Show which endpoints exist on this service',
                optionals('JSON',
                    [h_pretty],
                    [h_took, h_type_json])),
            capability(event,
                '/status',
                'Status',
                'Returns service status',
                optionals('418 I\'m a Teapot',
                    None,
                    [h_took, h_type_text])),
            capability(event,
                '/provider/<provider>',
                'Generate',
                'Generate a TEA config for provider',
                optionals('YAML',
                    [h_pretty] + [h_token],
                    [h_took, h_type_yaml])),
        ]
    }
    return aws_return_message(event, 200, body, start=start)

def debug(event, context):
    """ Return debug information about AWS in general """
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
    return aws_return_message(event, 200, body, start=start)

def health(event, context):
    """
    Provide an endpoint for testing service availability and for complicance with
    RFC 7168
    """
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
    * HTTP Header named 'Cmr-Token' with a CMR compatible token
    """
    start = datetime.datetime.now()

    provider = event['pathParameters'].get('id')
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
        'cmr-auth': token}

    return result
