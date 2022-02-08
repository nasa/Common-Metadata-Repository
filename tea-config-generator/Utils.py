
def get_env(env: dict):
    return env.get('cmr-url', 'https://cmr.earthdata.nasa.gov')

def get_s3_prefixes(collection):
    if 'DirectDistributionInformation' in collection:
        direct_dist = collection['DirectDistributionInformation']
        if 'S3BucketAndObjectPrefixNames' in direct_dist:
            return direct_dist['S3BucketAndObjectPrefixNames']
    return []

def add_to_dict(all_s3_prefix_groups_dict, s3_prefixes_set, group_names_set):
    for s3_prefix in s3_prefixes_set:
        if s3_prefix in all_s3_prefix_groups_dict:
            existing_groups_set = all_s3_prefix_groups_dict[s3_prefix]
            existing_groups_set.update(group_names_set)
        else:
            all_s3_prefix_groups_dict[s3_prefix] = group_names_set

def create_tea_config(all_s3_prefix_groups_dict):
    result_string = ''
    for key, value in all_s3_prefix_groups_dict.items():
        result_string += key
        result_string += ':\n'
        for group in value:
            result_string += '  - '
            result_string += group
            result_string += '\n'
    return result_string
