"""
Module contains functions to get ACLs
"""
import logging
import requests
import Utils as util

if len(logging.getLogger().handlers)>0:
    logging.basicConfig(filename='script.log', format='%(asctime)s %(message)s',
        encoding='utf-8', level=logging.INFO)

def get_acls(env,provider,token):
    """ Method used to get all ACLs for given provider """
    cmr_url = util.get_env(env)
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    url = f'{cmr_url}/access-control/acls?provider={provider}&\
            identity_type=catalog_item&pretty=true'
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

def get_acl(acl_url, token):
    """ Method retrieves ACL for given ACL URL """
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    try:
        response = requests.get(acl_url, headers=headers)
        json_data = response.json()
        logging.debug("get_acl: response=%s", json_data)
        if response.status_code == 200:
            return json_data
    except requests.exceptions.RequestException as error:
        logging.error('Error occurred in get_acl: %s', error)
    return {}
