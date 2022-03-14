""" Utility methods """

import logging

def standard_headers(base:dict = None):
    """
    Return a dictionary containing the standard headers which should always be
    used when communicating to CMR from this app. Append to an existing dictionary
    if one exists.
    """
    if base is None:
        base = {}
    base['User-Agent'] = 'ESDIS TEA Config Generator'
    return base

def get_env(env: dict):
    """ Returns CMR server URL, uses 'https://cmr.earthdata.nasa.gov' as default """
    return env.get('cmr-url', 'https://cmr.earthdata.nasa.gov')

def get_s3_prefixes(collection):
    """ Returns array of S3 prefixes for given collection """
    if 'DirectDistributionInformation' in collection:
        direct_dist = collection['DirectDistributionInformation']
        if 'S3BucketAndObjectPrefixNames' in direct_dist:
            return direct_dist['S3BucketAndObjectPrefixNames']
    return []

def add_to_dict(all_s3_prefix_groups_dict, s3_prefixes_set, group_names_set):
    """ Adds new elements to S3 prefixes groups dictionary """
    for s3_prefix in s3_prefixes_set:
        if s3_prefix in all_s3_prefix_groups_dict:
            existing_groups_set = all_s3_prefix_groups_dict[s3_prefix]
            existing_groups_set.update(group_names_set)
        else:
            all_s3_prefix_groups_dict[s3_prefix] = group_names_set

def create_tea_config(all_s3_prefix_groups_dict):
    """ For given S3 prefixes groups dicionary creates the result string"""
    result_string = 'PRIVATE_BUCKETS:\n'
    for key, value in all_s3_prefix_groups_dict.items():
        result_string += ' '*2
        result_string += key.strip()
        result_string += ':\n'
        for group in value:
            result_string += ' '*4 + '- '# 4x space dash space
            result_string += group.strip()
            result_string += '\n'
    return result_string

def get_logger(envirnment):
    """
    Create a logger using the logging info from the calling environment, configure
    that logger and return it for use by the code.
    """
    level = envirnment.get('logging-level', 'INFO')
    logging.basicConfig(format="%(name)s - %(module)s - %(message)s",level=level)
    logger = logging.getLogger()
    logger.setLevel(level)
    return logger
