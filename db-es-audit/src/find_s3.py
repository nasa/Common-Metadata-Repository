import json
import os
import logging

import boto3
from botocore.exceptions import ClientError

logger = logging.getLogger("find_s3")
AUDIT_BUCKET_NAME = os.getenv("AUDIT_S3_BUCKET_NAME")

def upload_file_to_s3(file_name, object_name=None):
    """
    Upload a file to an S3 bucket

    Args:
        file_name: File to upload (full path on EFS, e.g., '/data/file_name.txt')
        object_name: S3 object name. If not specified then file_name is used

    Returns:
        False if there was an issue, otherwise True

    Exceptions:
        ClientError: Error when the file cannot be uploaded
        FileNotFoundError: Error when the file cannot be found.
    """

    logger.debug(f"save to S3: file_name: {file_name}, bucket: {AUDIT_BUCKET_NAME}, object_name {object_name}")
    # If S3 object_name was not specified, use file_name
    if object_name is None:
        object_name = os.path.basename(file_name)

    # Upload the file using the S3 client
    s3_client = boto3.client('s3')
    try:
        # Boto3's upload_file handles large files by splitting them into smaller chunks and uploading each in parallel
        s3_client.upload_file(file_name, AUDIT_BUCKET_NAME, object_name)
        logger.debug(f"Successfully uploaded {file_name} to s3://{AUDIT_BUCKET_NAME}/{object_name}")
        return True
    except ClientError as e:
        logger.error(e)
        logger.error(f"Failed to upload {file_name} to s3://{AUDIT_BUCKET_NAME}/{object_name}")
        return False
    except FileNotFoundError:
        logger.error(f"The file {file_name} was not found when trying to upload to S3.")
        return False

def save_to_s3(s3_client, data, provider):
    """
    Save the passed in data which is a json dict that holds the provider name and a list of their collection concept ids.

    Args:
        s3_client: the s3 client.
        data: The data to save to the S3 bucket
        provider: The provider name used as a directory name.

    Returns:
        Nothing

    Exceptions:
        None
    """

    # Convert dict to JSON string and upload
    s3_client.put_object(
        Bucket=f"{AUDIT_BUCKET_NAME}",
        Key=f"{provider}/{provider}_collections.json",
        Body=json.dumps(data).encode('utf-8'),
        ContentType='application/json'
    )

def save_providers_to_s3(s3_client, data):
    """
    Saves a json array of provider names to S3.

    Args:
        s3_client: the s3 client.
        data: The data to save to the S3 bucket 

    Returns:
        Nothing

    Exceptions:
        None
    """

    # Convert dict to JSON string and upload
    s3_client.put_object(
        Bucket=f"{AUDIT_BUCKET_NAME}",
        Key="providers.json",
        Body=json.dumps(data).encode('utf-8'),
        ContentType='application/json'
    )

def read_from_s3(s3_client, object_key):
    """
    Read a file from S3.

    Args:
        s3_client: the s3 client.
        object_key: The file name that exists on S3.

    Returns:
        The contents of the file.

    Exceptions:
        General exception if the file cannot be read.
    """

    try:
        response = s3_client.get_object(Bucket=AUDIT_BUCKET_NAME, Key=object_key)

        # Read the content
        object_content = response['Body'].read().decode('utf-8')
        return object_content

    except Exception as e:
        logger.error(f"Error reading object {object_key} from bucket {AUDIT_BUCKET_NAME}: {e}")

def read_providers_from_s3(s3_client):
    """
    Read the providers.json file from S3.

    Args:
        s3_client: the s3 client.

    Returns:
        The contents of the file.

    Exceptions:
        General exception from the read_from_s3 function if the file cannot be read.
    """

    providers_str = read_from_s3(s3_client, "providers.json")
    return json.loads(providers_str)

def read_collections_from_provider(s3_client, provider):
    """
    Read a providers list of collections file from S3.

    Args:
        s3_client: the s3 client.
        provider: used to find the correct directory and filename

    Returns:
        The contents of the file.

    Exceptions:
        General exception from the read_from_s3 function if the file cannot be read.
    """

    collections_str = read_from_s3(s3_client, f"{provider}/{provider}_collections.json")
    return json.loads(collections_str)

