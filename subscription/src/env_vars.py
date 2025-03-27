import os
import boto3
from botocore.exceptions import ClientError
from sys import stdout
from logger import logger

class Env_Vars:
    """Encapsulates Accessing Variables first from the OS
    if not there, then the parameter store."""

    def __init__(self):
        self.ssm_client = boto3.client('ssm', region_name=os.getenv("AWS_REGION"))
    
    def get_env_var_from_parameter_store(self, parameter_name, decryption=False):
        """The name parameter looks like /sit/ingest/ENVIRONMENT_VAR. To check if the environment
           variable exists strip off everything except for the actual variable name. Otherwise
           go to the AWS ParameterStore and get the values."""

        logger.debug(f"Subscription worker: Getting the environment variable called {parameter_name}")
        value = os.getenv(parameter_name.split('/')[-1])

        if not value:
            try:
                # Get the parameter value from AWS Parameter Store
                response = self.ssm_client.get_parameter(Name=parameter_name, WithDecryption=decryption)
                return response['Parameter']['Value']
            
            except ClientError as e:
                logger.error(f"Error retrieving parameter {parameter_name} from AWS Parameter Store: {e}")
                raise
        else:
            return value
