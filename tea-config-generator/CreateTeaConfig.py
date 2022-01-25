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
        acls = get_acls(env, provider, token)
        for acl in acls:
            acl_url = acl['location']
            print('---------------------')
            print(f'Found ACL {acl_url}')
            acl_json = get_acl(acl_url, token)
            catalog_item_identity = acl_json['catalog_item_identity']
            if 'collection_identifier' in catalog_item_identity:
                print('Getting group names for ACL')
                group_names_set = set()
                group_names = get_group_names(acl_json, env, token)
                group_names_set.update(group_names)
                concept_ids = catalog_item_identity['collection_identifier']['concept_ids']
                s3_prefixes_set = set()
                for concept_id in concept_ids:
                    print(f'Found concept id in ACL: {concept_id}')
                    collection = get_collection(env, token, concept_id)
                    col_s3_prefixes = get_s3_prefixes(collection)
                    if col_s3_prefixes:
                        print(f'Found S3 prefixes: {col_s3_prefixes}')
                        s3_prefixes_set.update(col_s3_prefixes)
                    else:
                        print(f'No S3 prefixes found for concept id {concept_id}')
                if s3_prefixes_set:
                    print(f'Found S3 prefixes for ACL: {s3_prefixes_set}')
                    add_to_dict(all_s3_prefix_groups_dict, s3_prefixes_set, group_names_set)
                else:
                    print('No S3 prefixes found for ACL')
            else:
                print('ACL does not have concept ids assigned')
        if all_s3_prefix_groups_dict:
            logging.info(f'result mapping:\n{create_tea_config(all_s3_prefix_groups_dict)}')
        else:
            logging.info('No S3 prefixes found')

#env = 'uat'
#provider = 'SCIOPS'
logging.basicConfig(filename='script.log', format='%(asctime)s %(message)s', encoding='utf-8', level=logging.INFO)
provider = input('Enter provider: ')
env = input('Enter env (sit, uat or prod): ')
token = input('Enter EDL token: ')

processor = CreateTeaConfig()
processor.create_tea_config(env, provider, token)
