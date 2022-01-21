import requests

def get_acls(env,provider,token):
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    url = f'https://cmr.{env}earthdata.nasa.gov/access-control/acls?provider={provider}&identity_type=catalog_item&pretty=true'
    try:
        response = requests.get(url, headers=headers)
        print(f'get_acls: response code={response.status_code}')
        if response.status_code == 200:
            json_data = response.json()
            if 'items' in json_data:
                items = json_data['items']
                return items
    except Exception as e:
        print(f'Error occurred in get_acls: {e}')
    return []

def get_acl(acl_url, token):
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    try:
        response = requests.get(acl_url, headers=headers)
        print(f'get_acl: response code={response.status_code}')
        if response.status_code == 200:
            json_data = response.json()
            return json_data
    except Exception as e:
        print(f'Error occurred in get_acl: {e}')
    return {}
