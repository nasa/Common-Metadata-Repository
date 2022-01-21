import requests

def get_collection(env, token, concept_id):
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    url = f'https://cmr.{env}earthdata.nasa.gov/search/concepts/{concept_id}.umm_json?pretty=true'
    try:
        response = requests.get(url, headers=headers)
        if response.status_code == 200:
            json_data = response.json()
            return json_data
    except Exception as e:
        print(f'Error occurred in get_collection: {e}')
    return {}
