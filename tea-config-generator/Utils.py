def get_env(env):
    if env is None or env=='' or env=='ops' or env=='prod' or env=='production':
        env = ''
    if env != '' and not env.endswith('.'):
        env = env + '.'
    return env

def get_s3_prefixes(collection):
    if 'DirectDistributionInformation' in collection:
      direct_dist = collection['DirectDistributionInformation']
      if 'S3BucketAndObjectPrefixNames' in direct_dist:
        return direct_dist['S3BucketAndObjectPrefixNames']
    return []
