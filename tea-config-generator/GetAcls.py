import requests
import logging
import Utils as util

if len(logging.getLogger().handlers)>0:
    logging.basicConfig(filename='script.log', format='%(asctime)s %(message)s',
        encoding='utf-8', level=logging.INFO)

def get_acls(env,provider,token):
    cmr_url = util.get_env(env)
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    url = f'{cmr_url}/access-control/acls?provider={provider}&identity_type=catalog_item&pretty=true'
    try:
        response = requests.get(url, headers=headers)
        json_data = response.json()
        logging.debug(f'get_acls: response={json_data}')
        if response.status_code == 200:
            if 'items' in json_data:
                items = json_data['items']
                return items
    except Exception as error:
        print(f'Error occurred in get_acls: {error}')
    return []

def get_acl(acl_url, token):
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    try:
        response = requests.get(acl_url, headers=headers)
        json_data = response.json()
        logging.debug(f'get_acl: response={json_data}')
        if response.status_code == 200:
            return json_data
    except Exception as error:
        print(f'Error occurred in get_acl: {error}')
    return {}
