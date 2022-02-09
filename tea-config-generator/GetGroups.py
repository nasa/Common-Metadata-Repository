import requests
import logging
import Utils as util

#logging.basicConfig(filename='script.log', format='%(asctime)s %(message)s', encoding='utf-8', level=logging.INFO)

def get_groups(acl_url, token):
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    try:
        response = requests.get(acl_url, headers=headers)
        json_data = response.json()
        logging.debug(f'get_groups: response={json_data}')
        if response.status_code == 200:
            if 'group_permissions' in json_data:
                items = json_data['group_permissions']
                return items
    except Exception as e:
        print(f'Error occurred in get_groups: {e}')
    return []

def get_group(env:dict, group_id, token):
    cmr_base = util.get_env(env)
    url = f'{cmr_base}/access-control/groups/{group_id}?pretty=true'
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    try:
        response = requests.get(url, headers=headers)
        json_data = response.json()
        logging.debug(f'get_group: response={json_data}')
        if response.status_code == 200:
            return json_data
    except Exception as e:
        print(f'Error occurred in get_group: {e}')
    return {}

def get_group_names(acl_json, env, token):
    group_names = []
    if 'group_permissions' in acl_json:
        all_groups = acl_json['group_permissions']
        for group in all_groups:
            if ('group_id' in group) and (not group['group_id'].endswith('-CMR')):
                group_json = get_group(env, group['group_id'], token)
                group_name = group_json['name']
                group_names.append(group_name)
                logging.info(f'Found non-CMR group: {group_name}')
    return group_names
