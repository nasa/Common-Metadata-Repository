import sys
import boto3
import logging

# This variable stores the SSM parameters
CONFIG = {}

logger = logging.getLogger("parameters")

def load_ssm_params(environment):
    """
    Loads the AWS parameter store values into the CONFIG global variable that are
    used through out the program.

    Args:
        environment: The name of the CMR environment such as sit or uat. It is used
        to get the correct parameter store data.
    
    Returns:
        Nothing; But populates a global config that holds all of the parameters.

    Exceptions:
        None; The Config is checked to see if it exists. If not then we know that
        the parameter store values were not loaded and therefore the issue is logged
        and the program terminated.
    """
    ssm = boto3.client("ssm")
    logger.info(f"Checking the environment: {environment}")
    path_prefix = f"/{environment}/audit/"

    paginator = ssm.get_paginator('get_parameters_by_path')
    page_iterator = paginator.paginate(
        Path=path_prefix,
        WithDecryption=True,
        Recursive=True
    )

    new_values = {}
    for page in page_iterator:
        for param in page['Parameters']:
            # Extract the simple name from the full path
            key = param['Name'].split('/')[-1]
            new_values[key] = param['Value']

    elastic_port = new_values.get('ELASTIC_PORT', 9200)
    gran_elastic_port = new_values.get('GRAN_ELASTIC_PORT', 9200)
    batch_size = new_values.get('BATCH_SIZE', 1000)

    new_values["ELASTIC_URL"] = f"http://{new_values.get("ELASTIC_HOST")}:{elastic_port}"
    new_values["GRAN_ELASTIC_URL"] = f"http://{new_values.get("GRAN_ELASTIC_HOST")}:{gran_elastic_port}"
    new_values["BATCH_SIZE"] = int(batch_size)

    # use update - this ensures that all other files that import the config
    # get the most up to data values.
    CONFIG.update(new_values)

    if not CONFIG:
        logger.error("The environment variables were not loaded.")
        sys.exit(1)
