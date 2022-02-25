""" Main module to create TEA config """
import logging
import sys
from tea.gen.utils import add_to_dict, create_tea_config
from tea.gen.get_acls import get_acl, get_acls
from tea.gen.get_groups import get_group_names
from tea.gen.get_collections import get_collections_s3_prefixes_dict

#pylint: disable=E1101 #No issue, just want number of handlers
#pylint: disable=R0914 #We want parameters separated
#pylint: disable=R0201 #We want it as class method
#pylint: disable=R0903 #No point to add more public methods
class CreateTeaConfig:
    """ Main class to create TEA config """
    def __init__(self, env):
        logging.basicConfig(level=env['logging-level'],
            format="%(name)s - %(module)s - %(message)s")
        logging.debug('Creating TEA configuraton')

    def create_tea_config(self, env:dict, provider:str, token:str):
        """ Main method to retrieve data and create TEA config """
        all_s3_prefix_groups_dict = {}
        all_collections_s3_prefixes_dict = \
            get_collections_s3_prefixes_dict(env, token, provider, 1, 2000)
        if not all_collections_s3_prefixes_dict:
            return {'statusCode': 404, 'body': 'No S3 prefixes returned'}
        acls = get_acls(env, provider, token)
        for acl in acls:
            acl_url = acl['location']
            logging.info('---------------------')
            logging.info('Found ACL %s', acl_url)
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
                    logging.info('Found concept id in ACL: %s', concept_id)
                    if concept_id in all_collections_s3_prefixes_dict:
                        col_s3_prefixes = all_collections_s3_prefixes_dict[concept_id]
                        logging.info('Found S3 prefixes: %s', col_s3_prefixes)
                        s3_prefixes_set.update(col_s3_prefixes)
                    else:
                        logging.info('No S3 prefixes found for concept id %s', concept_id)
                if s3_prefixes_set:
                    logging.info('Number of S3 prefixes for ACL: %d', len(s3_prefixes_set))
                    logging.info('Found Group Name Set for ACL: %s', group_names_set)
                    if group_names_set:
                        add_to_dict(all_s3_prefix_groups_dict, s3_prefixes_set, group_names_set)
                else:
                    logging.info('No S3 prefixes found for ACL')
            else:
                logging.info('ACL does not have concept ids assigned')
        if all_s3_prefix_groups_dict:
            tea_config_text = create_tea_config(all_s3_prefix_groups_dict)
            logging.info('result mapping:\n%s', tea_config_text)
            return {'statusCode': 200, 'body': tea_config_text}

        logging.info('No S3 prefixes found')
        return {'statusCode': 404, 'body': 'No S3 prefixes found'}

def main():
    """ Main method - a direct unit test """
    if len(logging.getLogger().handlers) > 0:
        logging.getLogger.setLevel(logging.INFO)
    else:
        logging.basicConfig(filename='script.log', \
            format='%(asctime)s %(message)s', \
            encoding='utf-8', \
            level=logging.INFO)

    provider = input('Enter provider: ')
    if provider is None:
        provider = 'POCLOUD'

    cmr_env = input('Enter env (sit, uat or prod): ')
    if cmr_env is None:
        cmr_env = 'uat'

    token = input('Enter EDL token: ')
    if token is None:
        print ('A CMR compatable token must be provided')
        sys.exit()

    cmrs = {'sit':'https://cmr.sit.earthdata.nasa.gov',
        'uat':'https://cmr.uat.earthdata.nasa.gov',
        'ops':'https://cmr.earthdata.nasa.gov',
        'prod':'https://cmr.earthdata.nasa.gov'}

    env = {'cmr-url': cmrs[cmr_env.lower()]}
    env['logging-level'] = 'info'
    env['pretty-print'] = True

    processor = CreateTeaConfig(env)
    processor.create_tea_config(env, provider, token)

if __name__ == "__main__":
    main()
