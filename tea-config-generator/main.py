""" Main method to run the Lambda functions in handler.py."""

import logging
import os
import argparse

import handler

# ******************************************************************************
#mark - utility functions

def handle_arguments():
    """
    Setup the application parameteres and return a parsed args object allowing
    the caller to get the param values
    """
    parser = argparse.ArgumentParser(description='Test AWS handler functions')

    aws_group = parser.add_argument_group()
    aws_group.add_argument('-l', '--log-level', default='debug',
        help='Set the python log level')
    aws_group.add_argument('-p', '--provider', default='POCLOUD',
        help='CMR Provider name, such as POCLOUD')
    aws_group.add_argument('-e', '--env', default='uat', help='sit, uat, ops')

    token_ops = parser.add_mutually_exclusive_group()
    token_ops.add_argument('-t', '--token', default='', help='EDL Token')
    token_ops.add_argument('-tf', '--token-file', default='',
        help='Path to a file holding an EDL Token')

    args = parser.parse_args()
    return args

def read_token(args):
    """ Read a token file or token parameter and return the content """
    if args.token_file:
        with open(args.token_file, encoding='utf8') as file_obj:
            token = file_obj.readline().strip()
    elif args.token:
        token = args.token
    else:
        token = None
    return token

def setup_logging(log_level):
    """ Used to test functions locally """
    if len(logging.getLogger().handlers)>0:
        logging.getLogger().setLevel(log_level)
    else:
        logging.basicConfig(filename='script.log',
            format="%(name)s - %(module)s - %(message)s",
            level=log_level)
            #format='%(asctime)s %(message)s',

def cmr_url_for_env(which_cmr):
    """ Return the environment specific URL for CMR """
    cmrs = {'sit':'https://cmr.sit.earthdata.nasa.gov',
        'uat':'https://cmr.uat.earthdata.nasa.gov',
        'ops':'https://cmr.earthdata.nasa.gov',
        'prod':'https://cmr.earthdata.nasa.gov'}
    return cmrs.get(which_cmr, 'https://cmr.earthdata.nasa.gov')

# ******************************************************************************
#mark - command line function

def main():
    """
    This method is just to test the lambda functions by passing along the defined
    input values in a way as to emulate AWS. Currently the generate_tea_config()
    is run.
    """
    args = handle_arguments()

    provider = args.provider
    which_cmr = args.env
    token = read_token(args)
    log_level = args.log_level.upper()

    setup_logging(log_level)
    cmr_url = cmr_url_for_env(which_cmr)

    # Setup environment and parameters
    os.environ['AWS_TEA_CONFIG_LOG_LEVEL'] = log_level
    os.environ['AWS_TEA_CONFIG_CMR'] = cmr_url
    event = {'headers': {'authorization': token},
        'pathParameters': {'id': provider},
        'path': f'/configuration/tea/provider/{provider}'}
    context = {}

    print(handler.generate_tea_config(event, context))

if __name__ == "__main__":
    main()
