import os
import json
import requests
from env_vars import Env_Vars
from sys import stdout
from logger import logger

class AccessControl:
    """Encapsulates Access Control API.
    This class needs the following environment variables set with an example value:
    For local development:
    ACCESS_CONTROL_URL=http://localhost:3011/access-control
    
    For AWS:
    ENVIRONMENT_NAME=sit
    CMR_ACCESS_CONTROL_PROTOCOL=https
    CMR_ACCESS_CONTROL_PORT=3011
    CMR_ACCESS_CONTROL_HOST=cmr.sit.earthdata.nasa.gov
    CMR_ACCESS_CONTROL_RELATIVE_ROOT_URL=access-control

    Example Use of this class
    access_control = AccessControl()
    response = access_control.get_permissions('user1', 'C1200484253-CMR_ONLY')
    The call is the same as 'curl https://cmr.sit.earthdata.nasa.gov/access-control/permissions?user_id=user1&concept_id=C1200484253-CMR_ONLY'
    Return is either None (Null or Nil) (if check on response is false) or
    {"C1200484253-CMR_ONLY":["read","update","delete","order"]}
    """

    
    def __init__(self):
        """ Sets up a class variable of url."""
        self.url = None

    def get_url_from_parameter_store(self):
        """This function returns the URL for the accees control service. For local development the full URL can be provided. Otherwise the 
        environment name that is used for the parameter store prefix is obtained from an environment variable. This variable is used to 
        get the parameter store ingest values to construct the access control service URL."""

        # Access Control URL is for local development
        access_control_url = os.getenv("ACCESS_CONTROL_URL")

        if access_control_url:
            self.url = access_control_url
            logger.debug(f"Subscription Worker Access-Control URL: {self.url}")
            return
        else:
            # This block gets the access_control URL from the AWS parameter store.
            environment_name = os.getenv("ENVIRONMENT_NAME")

            if not environment_name:
                logger.error("ENVIRONMENT_NAME environment variable is not set")
                raise ValueError("ENVIRONMENT_NAME environment variable is not set")

            # construct the access control parameter names from the environment variable
            env_name = environment_name.lower()
            pre_fix = f"/{env_name}/ingest/"
            protocol_param_name = f"{pre_fix}CMR_ACCESS_CONTROL_PROTOCOL"
            port_param_name = f"{pre_fix}CMR_ACCESS_CONTROL_PORT"
            host_param_name = f"{pre_fix}CMR_ACCESS_CONTROL_HOST"
            context_param_name = f"{pre_fix}CMR_ACCESS_CONTROL_RELATIVE_ROOT_URL"

            env_vars = Env_Vars()
            protocol = env_vars.get_var(name=protocol_param_name)
            port = env_vars.get_var(name=port_param_name)
            host = env_vars.get_var(name=host_param_name)
            context = env_vars.get_var(name=context_param_name)

            # The context already contains the forward / so we don't need it here.
            self.url = f"{protocol}://{host}:{port}{context}"
            logger.debug(f"Subscription Worker Access-Control URL: {self.url}")

    def get_url(self):
        """This function returns the access control URL if it has already been constructed, otherwise it constructs the URL and then returns it."""
        if not self.url:
            self.get_url_from_parameter_store()
        return self.url

    def get_permissions(self, subscriber_id, concept_id):
        """This function calls access control using a subscriber_id (a users earth data login name), and a CMR concept id. It gets the subscribers permission
        set for a specific concept. access control returns None|Nil|Null back if the subscriber does not have any permissions for the concept.  Access control
        returns a map that contains a concept id followed by an array of permissions the user has on that concept: {"C1200484253-CMR_ONLY":["read","update","delete","order"]}"""

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
            logger.debug(f"Response data: {data}")
            return data
        else:
            # Request failed
            logger.warning(f"Subscription Worker getting Access Control permissions request using URL {url} with parameters {params} failed with status code: {response.status_code}")

    def has_read_permission(self, subscriber_id, collection_concept_id):
        """This function calls access control using a subscriber_id (a users earth data login name), and a CMR concept id. It gets the subscribers permission
        set for a specific concept. access control returns None|Nil|Null back if the subscriber does not have any permissions for the concept.  Access control
        returns a map that contains a concept id followed by an array of permissions the user has on that concept: {"C1200484253-CMR_ONLY":["read","update","delete","order"]}
        This function returns true if the read permission exists, false otherwise."""

        try:
            # Call the get_permissions function
            permissions_str = self.get_permissions(subscriber_id, collection_concept_id)
            
            if permissions_str:
                permissions = json.loads(permissions_str)

                # Check if the permissions is a dictionary
                if isinstance(permissions, dict):
                    # Check if the collection_concept_id is in the permissions dictionary
                    if collection_concept_id in permissions:
                        # Check if "read" is in the list of permissions for the collection
                        return "read" in permissions[collection_concept_id]
                    else: return False
                else: return False
            else: return False

        except Exception as e:
            # Handle any exceptions that may occur (e.g., network issues, API errors)
            logger.error(f"Subscription Worker Access Control error getting permissions for subscriber {subscriber_id} on collection concept id {collection_concept_id}: {str(e)}")
            return False
