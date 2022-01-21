from GetAcls import *
from Utils import *
from GetGroups import *
from GetCollections import *

class CreateTeaConfig:
    def __init__(self):
        print('Object CreateTeaConfig created')

    def create_tea_config(self, env, provider, token):
        env = get_env(env)
        acls = get_acls(env, provider, token)
        for acl in acls:
            acl_url = acl['location']
            acl_json = get_acl(acl_url, token)
            catalog_item_identity = acl_json['catalog_item_identity']
            if 'collection_identifier' in catalog_item_identity:
                group_names = get_group_names(acl_json, env, token)
                concept_ids = catalog_item_identity['collection_identifier']['concept_ids']
                all_s3_prefixes = set()
                for concept_id in concept_ids:
                    print(f'concept id={concept_id}')
                    collection = get_collection(env, token, concept_id)
                    col_s3_prefixes = get_s3_prefixes(collection)
                    all_s3_prefixes.update(col_s3_prefixes)
                print(all_s3_prefixes)

env = 'uat'
provider = 'SCIOPS'
token = 'EDL-XXX'

processor = CreateTeaConfig()
processor.create_tea_config(env, provider, token)
