import json
import boto3
import gzip
from io import BytesIO

def _get_s3_data(bucket, key):
    '''
    This function gets a specific log file from the given S3 bucket and
    decompresses it.
    '''
    s3_client = boto3.client('s3')
    data = s3_client.get_object(Bucket=bucket, Key=key)['Body'].read()

    if key.split('.')[-1] == 'gz':
        data = gzip.GzipFile(fileobj=BytesIO(data)).read()

    return data.decode('UTF-8')

def export(event, context):
    bucket = event['Records'][0]['s3']['bucket']['name']
    key = event['Records'][0]['s3']['object']['key']

    data = _get_s3_data(bucket, key)
    print(data)

    return {
        "message": "exported CloudFront log to CloudWatch successfully!",
        "event": event
    }
