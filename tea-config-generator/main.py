"""
AWS lambda functions for generating a TEA configuration.
"""

import logging
import os
import json
import datetime
import boto3

import handler as handler
#import tea.gen.create_tea_config as tea

# ******************************************************************************
#mark - command line functions

def main():
    """ Unit testing, collect variables needed to run generate_tea_config() """
    #pylint: disable=C0415 # library is only needed for local execution, not AWS
    import argparse

    parser = argparse.ArgumentParser(description='Test handler')

    aws_group = parser.add_argument_group()
    aws_group.add_argument('-l', '--log-level', default='debug',
        help="Set the python log level")
    aws_group.add_argument('-p', '--provider', default='POCLOUD',
        help="CMR Provider name such as POCLOUD")
    aws_group.add_argument('-e', '--env', default='uat', help="sit, uat, ops")

    token_ops = parser.add_mutually_exclusive_group()
    token_ops.add_argument('-t', '--token', default='', help="EDL Token")
    token_ops.add_argument('-tf', '--token-file', default='',
        help="Path to a file holding an EDL Token")

    args = parser.parse_args()

    log_level = args.log_level.upper()
    provider = args.provider
    which_cmr = args.env

    if args.token_file:
        with open(args.token_file, encoding='utf8') as file_obj:
            token = file_obj.readline().strip()
    elif args.token:
        token = args.token
    else:
        token = None

    # Used to test functions locally
    if len(logging.getLogger().handlers)>0:
        logging.getLogger().setLevel(log_level)
    else:
        logging.basicConfig(filename='script.log',
            format='%(asctime)s %(message)s',
            level=log_level)

    cmrs = {'sit':'https://cmr.sit.earthdata.nasa.gov',
        'uat':'https://cmr.uat.earthdata.nasa.gov',
        'ops':'https://cmr.earthdata.nasa.gov',
        'prod':'https://cmr.earthdata.nasa.gov'}

    os.environ['AWS_TEA_CONFIG_LOG_LEVEL'] = log_level
    os.environ['AWS_TEA_CONFIG_CMR'] = cmrs[which_cmr]
    event = {'headers': {'Cmr-Token': token}, 'pathParameters': {'id': provider}}
    context = {}

    print(handler.generate_tea_config(event, context))

if __name__ == "__main__":
    main()
