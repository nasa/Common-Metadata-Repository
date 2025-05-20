import os
import json
import requests
from env_vars import Env_Vars
from sys import stdout
from typing import Dict
from logger import logger
import xml.etree.ElementTree as ET

class Search:
    """Encapsulates Search API.
    This class needs the following environment variables set with an example value:
    For local development:
    SEARCH_URL=http://localhost:3003/
    
    For AWS:
    ENVIRONMENT_NAME=sit
    CMR_SEARCH_PROTOCOL=https
    CMR_SEARCH_PORT=443
    CMR_SEARCH_HOST=cmr.sit.earthdata.nasa.gov
    CMR_SEARCH_RELATIVE_ROOT_URL=search/
    TOKEN

    Example Use of this class
    search = Search()
    response = search.get_concept('token', 'G1200484253-CMR_ONLY')
    The call is the same as 'curl -H "Authorization: <token>" https://cmr.sit.earthdata.nasa.gov/search/concepts/G1200484253-CMR_ONLY.umm_json'
    Return is either None (Null or Nil) (if check on response is false) or
    The concept in umm_json format}
    """

    def __init__(self):
        """ Sets up a class variable of url."""
        self.public_search_url = None
        self.token = None

    def get_public_search_url_from_parameter_store(self):
        """This function returns the URL for the public search service. For local development the full URL can be provided. Otherwise the 
        environment name that is used for the parameter store prefix is obtained from an environment variable. This variable is used to 
        get the parameter store ingest values to construct the search service URL."""

        # Search URL is for local development
        public_search_url = os.getenv("PUBLIC_SEARCH_URL")

        if public_search_url:
            self.public_search_url = public_search_url
            logger.debug(f"Subscription Worker Search URL: {self.public_search_url}")
            return
        else:
            # This block gets the search URL from the AWS parameter store.
            environment_name = os.getenv("ENVIRONMENT_NAME")

            if not environment_name:
                logger.error("ENVIRONMENT_NAME environment variable is not set")
                raise ValueError("ENVIRONMENT_NAME environment variable is not set")

            # construct the search parameter names from the environment variable
            env_name = environment_name.lower()
            pre_fix = f"/{env_name}/search/"
            protocol_param_name = f"{pre_fix}CMR_SEARCH_PUBLIC_PROTOCOL"
            port_param_name = f"{pre_fix}CMR_SEARCH_PUBLIC_PORT"
            host_param_name = f"{pre_fix}CMR_SEARCH_PUBLIC_HOST"
            context_param_name = f"{pre_fix}CMR_SEARCH_RELATIVE_ROOT_URL"

            env_vars = Env_Vars()
            protocol = env_vars.get_env_var_from_parameter_store(parameter_name=protocol_param_name)
            port = env_vars.get_env_var_from_parameter_store(parameter_name=port_param_name)
            host = env_vars.get_env_var_from_parameter_store(parameter_name=host_param_name)
            context = env_vars.get_env_var_from_parameter_store(parameter_name=context_param_name)

            # The context already contains the forward / so we don't need it here.
            self.public_search_url = f"{protocol}://{host}:{port}{context}"
            logger.debug(f"Subscription Worker Public Search URL: {self.public_search_url}")

    def get_public_search_url(self):
        """This function returns the public search URL if it has already been constructed, otherwise it constructs the URL and then returns it."""
        if not self.public_search_url:
            self.get_public_search_url_from_parameter_store()
        return self.public_search_url

    def get_token_from_parameter_store(self):
        """This function returns the token for the search service. Otherwise the 
        environment name that is used for the parameter store prefix is obtained from an environment variable. This variable is used to 
        get the parameter store ingest values to construct the access control service URL."""

        # token is for local development
        token = os.getenv("CMR_ECHO_SYSTEM_TOKEN")

        if token:
            self.token = token
            return
        else:
            # This block gets the token from the AWS parameter store.
            environment_name = os.getenv("ENVIRONMENT_NAME")

            if not environment_name:
                logger.error("ENVIRONMENT_NAME environment variable is not set")
                raise ValueError("ENVIRONMENT_NAME environment variable is not set")

            # construct the the token parameter names from the environment variable
            env_name = environment_name.lower()
            token_name = f"/{env_name}/ingest/CMR_ECHO_SYSTEM_TOKEN"

            env_vars = Env_Vars()
            self.token = env_vars.get_env_var_from_parameter_store(token_name, True)

    def get_token(self):
        """This function returns the token if it has already been constructed, otherwise it gets the token and then returns it."""
        if not self.token:
            self.get_token_from_parameter_store()
        return self.token

    def get_concept(self, concept_id, revision_id):
        """This function calls search using a token, and a CMR concept id to get
            a granule by concept id in umm_json format."""

        try:
            # Set the search concepts URL.
            if revision_id:
                url = f"{self.get_public_search_url()}/concepts/{concept_id}/{revision_id}.umm_json"
            else:
                url = f"{self.get_public_search_url()}/concepts/{concept_id}.umm_json"

            # Set the headers
            headers = {
                "Authorization": self.get_token(),
                "Client-Id": "subscription-worker",
                "User-Agent": "subscription-worker"
            }

            # Make a GET request
            response = requests.get(url, headers=headers)
        except Exception as e:
            logger.error(f"Subscription worker Search: There is a problem in get_concept when getting concept from the CMR concept_id {concept_id} revision_id {revision_id}. {e}")
            logger.error(f"Subscription worker Search: Stack trace {traceback.print_exc()}")
            raise ValueError(f"Subscription worker Search: Stack trace {traceback.print_exc()}")

        # Check if the request was successful
        if response.status_code == 200:
            # Request was successful
            data = response.text
            logger.debug(f"Subscription Worker got search response data: {data}")
            return data
        else:
            # Request failed
            logger.warning(f"Subscription Worker getting search concept using URL {url} failed with status code: {response.status_code}")

    def get_producer_granule_id(self, metadata):
        """Get the granule producer id from the metadata."""
        identifiers = metadata.get('DataGranule').get('Identifiers')
        pgi = None
        if identifiers:
            for identifier in identifiers:
                if identifier.get('IdentifierType') == 'ProducerGranuleId':
                    pgi = identifier.get('Identifier')
                    break
            if pgi:
                return pgi
            else:
                return None
        else:
            return None

    def process_message(self, message):
        """This function gets the Message value from
        a SQS message that contains: 
        "{\"concept-id\": \"G1200484356-ERICH_PROV\",
          \"revision-id\": \"1\",
          \"granule-ur\": \"SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01\",
          \"location\": \"http://localhost:3003/concepts/G1200484356-ERICH_PROV/39\"}"
        
        Get the concept-id value and the revision-id value if it exists, search for the concepts metadata, and
        get the producer granule id and remove the revision-id. Return a message dictionary such as:
        {\"concept-id\": \"G1200484356-ERICH_PROV\",
         \"granule-ur\": \"SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01\",
         \"producer-granule-id\": \"SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01.nc\",
         \"location\": \"http://localhost:3003/concepts/G1200484356-ERICH_PROV/39\"}
        """
        # Get the granule concept-id from the message.
        print(f"Search process_message message type {type(message)}")
        message_dict = json.loads(message)
        concept_id = message_dict["concept-id"]
        revision_id = message_dict.get("revision-id", None)
        # Get the concept from search
        result = self.get_concept(concept_id, revision_id)

        # Parse the JSON into dict.
        result_dict = json.loads(result)

        #Get the producer granule id
        pgi = self.get_producer_granule_id(result_dict)
        if revision_id:
            del message_dict['revision-id']
        if pgi:
            message_dict.update({"producer-granule-id": pgi})
        return message_dict
