import boto3
import multiprocessing
import os
import json
from flask import Flask, jsonify
from sns import Sns
from botocore.exceptions import ClientError
from access_control import AccessControl
from logger import logger
import traceback

AWS_REGION = os.getenv("AWS_REGION")
QUEUE_URL = os.getenv("QUEUE_URL")
DEAD_LETTER_QUEUE_URL = os.getenv("DEAD_LETTER_QUEUE_URL")
SUB_DEAD_LETTER_QUEUE_URL = os.getenv("SUB_DEAD_LETTER_QUEUE_URL")
LONG_POLL_TIME = os.getenv("LONG_POLL_TIME", "1")
SNS_NAME = os.getenv("SNS_NAME")

def receive_message(sqs_client, queue_url):
    """ Calls the queue to get one message from it to process the message. """
    response = sqs_client.receive_message(
        QueueUrl=queue_url,
        MaxNumberOfMessages=10,
        # Long Polling
        WaitTimeSeconds=(int (LONG_POLL_TIME)))

    if len(response.get('Messages', [])) > 0:
        logger.debug(f"Number of messages received: {len(response.get('Messages', []))}")
    return response

def delete_message(sqs_client, queue_url, receipt_handle):
    """ Calls the queue to delete a processed message. """
    sqs_client.delete_message(QueueUrl=queue_url, ReceiptHandle=receipt_handle)

def delete_messages(sqs_client, queue_url, messages):
    """ Calls the queue to delete a list of processed messages. """
    for message in messages.get("Messages", []):
        receipt_handle = message['ReceiptHandle']
        delete_message(sqs_client=sqs_client, queue_url=queue_url, receipt_handle=receipt_handle)

def process_messages(sns_client, topic, messages, access_control):
    """ Processes a list of messages that was received from a queue. Check to see if ACLs pass for the granule.
        If the checks pass then send the notification. """

    for message in messages.get("Messages", []):
        try:
            message_body = json.loads(message["Body"])

            message_attributes = message_body["MessageAttributes"]
            logger.debug(f"Subscription worker: Received message including attributes: {message_body}")

            subscriber = message_attributes['subscriber']['Value']
            collection_concept_id = message_attributes['collection-concept-id']['Value']

            #sets the time in milliseconds
            #start_access_control = (time.time() * 1000)
            acl_read = True #access_control.has_read_permission(subscriber, collection_concept_id)
            #logger.info(f"Subscription Worker access control duration {((time.time() * 1000) - start_access_control)} ms.")
            if( acl_read):
                #logger.debug(f"Subscription worker: {subscriber} has permission to receive granule notifications for {collection_concept_id}")
                message_body['Message'] = json.loads(message_body['Message'])
                message['Body'] = message_body
                sns_client.publish_message(topic, message)
            else:
                logger.warning(f"Subscription worker: {subscriber} does not have read permission to receive notifications for {collection_concept_id}.")
        except Exception as e:
            logger.error(f"Subscription worker: There is a problem in process messages {message}. {e}")
            logger.error(f"Subscription worker: Stack trace {traceback.print_exc()}")

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
                 try:
                     process_messages(sns_client=sns_client, topic=topic, messages=messages, access_control=access_control)
                     delete_messages(sqs_client=sqs_client, queue_url=QUEUE_URL, messages=messages)
                 except Exception as e:
                     # This exception has already been logged, but capturing the exception here so that the message won't be deleted if it can't be processed.
                     # Do not do anything with the exception here so that we can process the dead letter queue.
                     None
             
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
