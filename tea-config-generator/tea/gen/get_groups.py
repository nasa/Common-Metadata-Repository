"""
Module contains functions to get Groups
"""
import requests
import tea.gen.utils as util

#logging.basicConfig(filename='script.log', format='%(asctime)s %(message)s',
#encoding='utf-8', level=logging.INFO)

def get_groups(env:dict, acl_url, token):
    """ Method gets groups for given ACL URL """
    logger = util.get_logger(env)
    headers = util.standard_headers({'Authorization': token, 'Content-Type': 'application/json'})
    try:
        response = requests.get(acl_url, headers=headers)
        json_data = response.json()
        logger.debug('get_groups: response=%s', json_data)
        if response.status_code == 200:
            if 'group_permissions' in json_data:
                items = json_data['group_permissions']
                return items
    except requests.exceptions.RequestException as error:
        logger.error('Error occurred in get_groups: %s', error)
    return []

def get_group(env:dict, group_id, token):
    """ Method used to get group for given group ID """
    logger = util.get_logger({})
    cmr_base = util.get_env(env)
    url = f'{cmr_base}/access-control/groups/{group_id}'
    headers = util.standard_headers({'Authorization': token, 'Content-Type': 'application/json'})
    try:
        response = requests.get(url, headers=headers)
        json_data = response.json()
        logger.debug('get_group: response=%s', json_data)
        if response.status_code == 200:
            return json_data
    except requests.exceptions.RequestException as error:
        logger.error('Error occurred in get_group: %s', error)
    return {}

def get_group_names(env:dict, acl_json, token):
    """ Method used to get group names for given ACL json """
    logger = util.get_logger({})
    group_names = []
    if 'group_permissions' in acl_json:
        all_groups = acl_json['group_permissions']
        for group in all_groups:
            if ('group_id' in group) and (not group['group_id'].endswith('-CMR')):
                group_json = get_group(env, group['group_id'], token)
                if 'name' in group_json:
                    group_name = group_json['name']
                    group_names.append(group_name)
                    logger.info('Found non-CMR group: %s', group_name)
    return group_names
