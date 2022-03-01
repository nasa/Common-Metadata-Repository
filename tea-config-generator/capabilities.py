"""
AWS lambda function for generating a TEA configuration capabilities file
"""

import datetime
import handler

#pylint: disable=W0613 # AWS requires event and context, but these are not always used

# ******************************************************************************
#mark - Utility functions

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
    logger = handler.init_logging()
    logger.debug("capabilities have been loaded")

    start = datetime.datetime.now()

    h_pretty = header_line('Cmr-Pretty', 'format output with new lines',
        'string', ['true', 'false'])
    h_took = header_line('Cmr-Took', 'number of seconds used to process request', 'real')
    h_token = header_line('Authorization', 'CMR Compatable Bearer token')
    h_type_json = header_line('content-type', 'content mime-type', None, 'application/json')
    h_type_text = header_line('content-type', 'content mime-type', None, 'text/plain')
    h_type_yaml = header_line('content-type', 'content mime-type', None, 'text/yaml')

    # optional return values
    optionals = lambda r,i,o : {'headers-in':i,'headers-out':o,'response':r}

    body = {}
    handler.append_version(body)
    body['urls'] = [
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

    return handler.aws_return_message(event, 200, body, start=start)
