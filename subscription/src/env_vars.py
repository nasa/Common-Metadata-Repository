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
    
    def get_var(self, name, decryption=False):
        value = os.getenv(name)

        if not value:
            try:
                # Get the parameter value from AWS Parameter Store
                response = self.ssm_client.get_parameter(Name=name, WithDecryption=decryption)
                return response['Parameter']['Value']
            
            except ClientError as e:
                logger.error(f"Error retrieving parameter from AWS Parameter Store: {e}")
                raise
        else:
            return value
