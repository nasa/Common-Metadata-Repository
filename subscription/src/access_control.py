import os
import requests
from env_vars import Env_Vars
from sys import stdout
from logger import logger

class AccessControl:
    """Encapsulates Access Control API.
    This class needs the following environment variables set:
    For local development:
    ACCESS_CONTROL_URL=http://localhost:3011/access-control
    
    For AWS:
    ENVIRONMENT_NAME=SIT
    CMR_ACCESS_CONTROL_PROTOCOL=https
    CMR_ACCESS_CONTROL_PORT=3011
    CMR_ACCESS_CONTROL_HOST=cmr.sit.earthdata.nasa.gov
    CMR_ACCESS_CONTROL_RELATIVE_ROOT_URL=access-control

    Example Use of this class
    access_control = AccessControl()
    response = access_control.get_permissions('eereiter', 'C1200484253-CMR_ONLY')
    The call is the same as 'curl https://cmr.sit.earthdata.nasa.gov/access-control/permissions?user_id=eereiter&concept_id=C1200484253-CMR_ONLY'
    Return is either None (Null or Nil) (if check on response is false) or
    {"C1200484253-CMR_ONLY":["read","update","delete","order"]}
    """

    def __init__(self):
        self.url = None

    def get_url_from_parameter_store(self):
        # Access Control URL is for local development
        access_control_url = os.getenv("ACCESS_CONTROL_URL")

        if access_control_url:
            self.url = access_control_url
            return
        else:
            # This block gets the access_control URL from the AWS parameter store.
            environment_name = os.getenv("ENVIRONMENT_NAME")

            if not environment_name:
                logger.error("ENVIRONMENT_NAME environment variable is not set")
                raise ValueError("ENVIRONMENT_NAME environment variable is not set")

            # construct the access control parameter names from the environment variable
            pre_fix = f"/{environment_name}/ingest/"
            protocol_param_name = f"{pre_fix}CMR_ACCESS_CONTROL_PROTOCOL"
            port_param_name = f"{pre_fix}CMR_ACCESS_CONTROL_PORT"
            host_param_name = f"{pre_fix}CMR_ACCESS_CONTROL_HOST"
            context_param_name = f"{pre_fix}CMR_ACCESS_CONTROL_RELATIVE_ROOT_URL"

            env_vars = Env_Vars
            protocol = env_vars.get_var(protocol_param_name)
            port = env_vars.get_var(port_param_name)
            host = env_vars.get_var(host_param_name)
            context = env_vars.get_var(context_param_name)
            self.url = f"{protocol}://{host}:{port}/{context}"
            logger.debug("Subscription Worker Access-Control URL:" + self.url)

    def get_url(self):
        if not self.url:
            self.get_url_from_parameter_store()
        return self.url

    def get_permissions(self, subscriber_id, concept_id):
        # Set the access-control permissions URL.
        url = f"{self.get_url()}/permissions"

        # Set the parameters
        params = {
            "user_id": subscriber_id,
            "concept_id": concept_id
        }

        # Make a GET request with parameters
        response = requests.get(url, params=params)

        # Check if the request was successful
        if response.status_code == 200:
            # Request was successful
            data = response.text
            logger.debug("Response data:", data)
            return data
        else:
            # Request failed
            logger.warning(f"Subscription Worker getting Access Control permissions request using URL {url} with parameters {params} failed with status code: {response.status_code}")
