import logging
from GetAcls import *
from Utils import *
from GetGroups import *
from GetCollections import *

class CreateTeaConfig:
    def __init__(self):
        logging.info('Creating TEA configuraton')

    def create_tea_config(self, env, provider, token):
        env = get_env(env)
        all_s3_prefix_groups_dict = {}
        all_collections_s3_prefixes_dict = get_collections_s3_prefixes_dict(env, token, provider, 1, 2000)
        logging.info(f'length of all_collections_s3_prefixes_dict={len(all_collections_s3_prefixes_dict)}')
        if not all_collections_s3_prefixes_dict:
            return {'statusCode': 200, 'body': 'No S3 prefixes found'}
        acls = get_acls(env, provider, token)
        for acl in acls:
            acl_url = acl['location']
            logging.info('---------------------')
            logging.info(f'Found ACL {acl_url}')
            acl_json = get_acl(acl_url, token)
            catalog_item_identity = acl_json['catalog_item_identity']
            if 'collection_identifier' in catalog_item_identity:
                logging.info('Getting group names for ACL')
                group_names_set = set()
                group_names = get_group_names(acl_json, env, token)
                group_names_set.update(group_names)
                concept_ids = catalog_item_identity['collection_identifier']['concept_ids']
                s3_prefixes_set = set()
                for concept_id in concept_ids:
                    logging.info(f'Found concept id in ACL: {concept_id}')
                    if concept_id in all_collections_s3_prefixes_dict:
                        col_s3_prefixes = all_collections_s3_prefixes_dict[concept_id]
                        logging.info(f'Found S3 prefixes: {col_s3_prefixes}')
                        s3_prefixes_set.update(col_s3_prefixes)
                    else:
                        logging.info(f'No S3 prefixes found for concept id {concept_id}')
                if s3_prefixes_set:
                    logging.info(f'Number of S3 prefixes for ACL: {len(s3_prefixes_set)}')
                    logging.info(f'Found Group Name Set for ACL: {group_names_set}')
                    if group_names_set:
                        add_to_dict(all_s3_prefix_groups_dict, s3_prefixes_set, group_names_set)
                else:
                    logging.info('No S3 prefixes found for ACL')
            else:
                logging.info('ACL does not have concept ids assigned')
        if all_s3_prefix_groups_dict:
            tea_config_text = create_tea_config(all_s3_prefix_groups_dict)
            logging.info(f'result mapping:\n{tea_config_text}')
            return {'statusCode': 200, 'body': tea_config_text}

        logging.info('No S3 prefixes found')
        return {'statusCode': 200, 'body': 'No S3 prefixes found'}

logging.basicConfig(filename='script.log', format='%(asctime)s %(message)s', encoding='utf-8', level=logging.INFO)
provider = input('Enter provider: ')
env = input('Enter env (sit, uat or prod): ')
token = input('Enter EDL token: ')

processor = CreateTeaConfig()
result = processor.create_tea_config(env, provider, token)
