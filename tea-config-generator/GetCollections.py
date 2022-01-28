import requests
import logging

logging.basicConfig(filename='script.log', format='%(asctime)s %(message)s', encoding='utf-8', level=logging.INFO)

def get_collections_s3_prefixes_dict(env, token, provider, page_num, page_size):
    all_collections_s3_prefixes_dict = {}
    json_data = get_collections(env, token, provider, page_num, page_size)
    if json_data['hits'] == '0':
        return {}
    hits = json_data['hits']
    logging.debug(f"Hits={hits}")
    for item in json_data['items']:
        if 's3-links' in item['meta']:
            all_collections_s3_prefixes_dict[item['meta']['concept-id']] = item['meta']['s3-links']
    if page_size < hits:
        remainder = hits % page_size
        pages = (hits - remainder) / page_size
        pages = int(pages)
        index = 1
        while index < pages + 1:
            index += 1
            json_data = get_collections(env, token, provider, index, page_size)
            collections_s3_prefixes_dict = {}
            items = json_data['items']
            for item in items:
                if 's3-links' in item['meta']:
                    collections_s3_prefixes_dict[item['meta']['concept-id']] = item['meta']['s3-links']
            all_collections_s3_prefixes_dict.update(collections_s3_prefixes_dict)

    return all_collections_s3_prefixes_dict

def get_collections(env, token, provider, page_num, page_size):
    headers = {'Authorization': f'Bearer {token}'}
    url = f'https://cmr.{env}earthdata.nasa.gov/search/collections.umm-json?provider={provider}&sort_key=entry_title&pretty=true&page_num={page_num}&page_size={page_size}'
    try:
        logging.debug(f'request url: {url}')
        response = requests.get(url, headers=headers)
        json_data = response.json()
        logging.debug(f'get_collections: response={json_data}')
        if response.status_code == 200:
            return json_data
    except Exception as e:
        print(f'Error occurred in get_collections: {e}')
    return {}

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
