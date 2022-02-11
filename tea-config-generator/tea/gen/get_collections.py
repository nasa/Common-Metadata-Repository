""" Module used to get Collections """
import logging
import requests
import tea.gen.utils as util

#logging.basicConfig(filename='script.log', format='%(asctime)s %(message)s',
#encoding='utf-8', level=logging.INFO)

def get_collections_s3_prefixes_dict(env: dict, token, provider, page_num, page_size):
    """ Method returns a dictionary with concept_ids as keys and S3 prefixes array as values  """
    all_collections_s3_prefixes_dict = {}
    json_data = get_collections(env, token, provider, page_num, page_size)
    if 'hits' not in json_data or json_data['hits'] == '0':
        return {}
    hits = json_data['hits']
    logging.debug('Hits=%d',hits)
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
                    collections_s3_prefixes_dict[item['meta']['concept-id']] = \
                        item['meta']['s3-links']
            all_collections_s3_prefixes_dict.update(collections_s3_prefixes_dict)

    return all_collections_s3_prefixes_dict

def get_collections(env:dict, token, provider, page_num, page_size):
    """ Method returns collections for given provider """
    headers = {'Authorization': f'Bearer {token}'}
    cmr_base = util.get_env(env)
    url = f'{cmr_base}/search/collections.umm-json?provider={provider}&\
        sort_key=entry_title&pretty=true&page_num={page_num}&page_size={page_size}'
    try:
        logging.debug('request url: %s', url)
        response = requests.get(url, headers=headers)
        json_data = response.json()
        logging.debug('get_collections: response=%s', json_data)
        if response.status_code == 200:
            return json_data
    except requests.exceptions.RequestException as error:
        logging.error('Error occurred in get_collections: %s', error)
    return {}

def get_collection(env:dict, token, concept_id):
    """ Method returns collection for given concept_id """
    headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    cmr_base = util.get_env(env)
    url = f'{cmr_base}/search/concepts/{concept_id}.umm_json?pretty=true'
    try:
        response = requests.get(url, headers=headers)
        json_data = response.json()
        logging.debug('get_collection: response=%s', json_data)
        if response.status_code == 200:
            return json_data
    except requests.exceptions.RequestException as error:
        logging.error('Error occurred in get_collection: %s', error)
    return {}
