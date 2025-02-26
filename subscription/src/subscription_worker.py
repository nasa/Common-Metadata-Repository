import boto3
import multiprocessing
import os
import json
from flask import Flask, jsonify
from sns import Sns
from botocore.exceptions import ClientError
from access_control import AccessControl
from logger import logger

AWS_REGION = os.getenv("AWS_REGION")
QUEUE_URL = os.getenv("QUEUE_URL")
DEAD_LETTER_QUEUE_URL = os.getenv("DEAD_LETTER_QUEUE_URL")
SUB_DEAD_LETTER_QUEUE_URL = os.getenv("SUB_DEAD_LETTER_QUEUE_URL")
LONG_POLL_TIME = os.getenv("LONG_POLL_TIME", "10")
SNS_NAME = os.getenv("SNS_NAME")

def receive_message(sqs_client, queue_url):
    response = sqs_client.receive_message(
        QueueUrl=queue_url,
        MaxNumberOfMessages=1,
        # Long Polling
        WaitTimeSeconds=(int (LONG_POLL_TIME)))

    if len(response.get('Messages', [])) > 0:
        logger.debug(f"Number of messages received: {len(response.get('Messages', []))}")
    return response

def delete_message(sqs_client, queue_url, receipt_handle):
    sqs_client.delete_message(QueueUrl=queue_url, ReceiptHandle=receipt_handle)

def delete_messages(sqs_client, queue_url, messages):
    for message in messages.get("Messages", []):
        receipt_handle = message['ReceiptHandle']
        delete_message(sqs_client=sqs_client, queue_url=queue_url, receipt_handle=receipt_handle)

def process_messages(sns_client, topic, messages, access_control):
    """Proess the message by first checking if the subscriber has permission to 
       see the notification. If so send it on, otherwise send a log message."""
    for message in messages.get("Messages", []):
        try:
            message_body = json.loads(message["Body"])
            message_attributes = message_body["MessageAttributes"]
            logger.debug(f"Subscription worker: Received message including attributes: {message_body}")

            subscriber = message_attributes['subscriber']['Value']
            collection_concept_id = message_attributes['collection-concept-id']['Value']

            if(access_control.has_read_permission(subscriber, collection_concept_id)):
                logger.debug(f"Subscription worker: {subscriber} has permission to receive granule notifications for {collection_concept_id}")
                sns_client.publish_message(topic, message)
            else:
                logger.info(f"Subscription worker: {subscriber} does not have read permission to receive notifications for {collection_concept_id}.")
        except Exception as e:
            logger.error(f"Subscription worker: There is a problem process messages {message}. {e}")
        

def poll_queue(running):
    """ Poll the SQS queue and process messages. """

    sqs_client = boto3.client("sqs", region_name=AWS_REGION)
    sns_resource = boto3.resource("sns", region_name=AWS_REGION)
    sns_client = Sns(sns_resource)
    logger.info(f"The passed in topic name is {SNS_NAME}")
    topic = sns_client.create_topic(SNS_NAME)
    
    access_control = AccessControl()
    while running.value:
        try:
             # Poll the SQS
             messages = receive_message(sqs_client=sqs_client, queue_url=QUEUE_URL)

             if messages:
                 process_messages(sns_client=sns_client, topic=topic, messages=messages, access_control=access_control)
                 delete_messages(sqs_client=sqs_client, queue_url=QUEUE_URL, messages=messages)

             dl_messages = receive_message(sqs_client=sqs_client, queue_url=DEAD_LETTER_QUEUE_URL)
             if dl_messages:
                 process_messages(sns_client=sns_client, topic=topic, messages=dl_messages, access_control=access_control)
                 delete_messages(sqs_client=sqs_client, queue_url=DEAD_LETTER_QUEUE_URL, messages=dl_messages)

        except Exception as e:
             logger.error(f"An error occurred receiving or deleting messages: {e}")

app = Flask(__name__)
@app.route('/shutdown', methods=['POST'])
def shutdown():
    """ Gracefully shutdown the polling process."""

    #Set the shared variable to False, shutting down the process
    running.value = False
    return jsonify({'status': 'shutting down'})

#Shared boolean value for process communication
running = multiprocessing.Value('b',True)

if __name__ == "__main__":
    logger.info("The subscription worker is starting to poll the SQS queue...")
    # Start the polling process
    poll_process = multiprocessing.Process(target=poll_queue, args=(running,))
    poll_process.start()

    # Start the Flask app in the main process
    # Expose the app on all interfaces
    app.run(host='0.0.0.0', port=5000)
    
    # Wait for the polling process to finish before exiting
    poll_process.join()
    logger.info("The subscription worker exited the polling loop.")

