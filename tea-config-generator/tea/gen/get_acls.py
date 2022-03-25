"""
Module contains functions to get ACLs
"""
import logging
import requests
import tea.gen.utils as util

def get_acls(env,provider,token):
    """ Method used to get all ACLs for given provider """

    logger = util.get_logger(env)
    logger.debug('TEA configuraton ACL')
    cmr_url = util.get_env(env)
    headers = util.standard_headers({'Authorization': token, 'Content-Type': 'application/json'})
    url = (f'{cmr_url}/access-control/acls'
            f'?provider={provider}'
            f'&identity_type=catalog_item'
            f'&page_size=2000')
    try:
        response = requests.get(url, headers=headers)
        json_data = response.json()
        logging.debug('get_acls: response=%s', json_data)
        if response.status_code == 200:
            if 'items' in json_data:
                items = json_data['items']
                return items
    except requests.exceptions.RequestException as error:
        logging.error('Error occurred in get_acl: %s', error)
    return []

def get_acl(env,acl_url, token):
    """ Method retrieves ACL for given ACL URL """
    logger = util.get_logger(env)
    headers = util.standard_headers({'Authorization': token, 'Content-Type': 'application/json'})
    try:
        response = requests.get(acl_url, headers=headers)
        json_data = response.json()
        logger.debug("get_acl: response=%s", json_data)
        if response.status_code == 200:
            return json_data
    except requests.exceptions.RequestException as error:
        logging.error('Error occurred in get_acl: %s', error)
    return {}
