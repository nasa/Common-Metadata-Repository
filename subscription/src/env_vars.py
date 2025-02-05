import os
import boto3
from botocore.exceptions import ClientError
from sys import stdout

class Env_Vars:
    """Encapsulates Accessing Variables first from the OS
    if not there, then the parameter store."""

    def __init__(self):
        self.ssm_client = boto3.client('ssm')
    
    def get_var(self, name, decryption=False):
        value = os.getenv(name)
        if value:
            print("Value: " + value)
        else:
            print("No Value")


        if not value:
            try:
                # Get the parameter value from AWS Parameter Store
                response = self.ssm_client.get_parameter(Name=name, WithDecryption=decryption)
                value = response['Parameter']['Value']
                print("if Value: " + value)
                return value
            
            except ClientError as e:
                print(f"Error retrieving parameter from AWS Parameter Store: {e}")
                stdout.flush()
                raise
        else:
            print("Else Value: " + value)
            return value
