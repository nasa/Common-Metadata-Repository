"""
Module contains functions to get Groups
"""
import logging
import requests
import Utils as util

#logging.basicConfig(filename='script.log', format='%(asctime)s %(message)s',
#encoding='utf-8', level=logging.INFO)

def get_groups(acl_url, token):
    """ Method gets groups for given ACL URL """
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    try:
        response = requests.get(acl_url, headers=headers)
        json_data = response.json()
        logging.debug('get_groups: response=%s', json_data)
        if response.status_code == 200:
            if 'group_permissions' in json_data:
                items = json_data['group_permissions']
                return items
    except requests.exceptions.RequestException as error:
        logging.error('Error occurred in get_groups: %s', error)
    return []

def get_group(env:dict, group_id, token):
    """ Method used to get group for given group ID """
    cmr_base = util.get_env(env)
    url = f'{cmr_base}/access-control/groups/{group_id}?pretty=true'
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    try:
        response = requests.get(url, headers=headers)
        json_data = response.json()
        logging.debug('get_group: response=%s', json_data)
        if response.status_code == 200:
            return json_data
    except requests.exceptions.RequestException as error:
        logging.error('Error occurred in get_group: %s', error)
    return {}

def get_group_names(acl_json, env, token):
    """ Method used to get group names for given ACL json """
    group_names = []
    if 'group_permissions' in acl_json:
        all_groups = acl_json['group_permissions']
        for group in all_groups:
            if ('group_id' in group) and (not group['group_id'].endswith('-CMR')):
                group_json = get_group(env, group['group_id'], token)
                if 'name' in group_json:
                    group_name = group_json['name']
                    group_names.append(group_name)
                    logging.info('Found non-CMR group: %s', group_name)
    return group_names
