import requests
import logging

logging.basicConfig(filename='script.log', format='%(asctime)s %(message)s', encoding='utf-8', level=logging.INFO)

def get_collection(env, token, concept_id):
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    url = f'https://cmr.{env}earthdata.nasa.gov/search/concepts/{concept_id}.umm_json?pretty=true'
    try:
        response = requests.get(url, headers=headers)
        json_data = response.json()
        logging.debug(f'get_collection: response={json_data}')
        if response.status_code == 200:
            return json_data
    except Exception as e:
        print(f'Error occurred in get_collection: {e}')
    return {}
