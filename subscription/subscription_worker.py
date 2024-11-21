import boto3
import multiprocessing
import os
from flask import Flask, jsonify
from sns import Sns
from sys import stdout
from botocore.exceptions import ClientError

AWS_REGION = os.getenv("AWS_REGION")
QUEUE_URL = os.getenv("QUEUE_URL")
DEAD_LETTER_QUEUE_URL = os.getenv("DEAD_LETTER_QUEUE_URL")
SUB_DEAD_LETTER_QUEUE_URL = os.getenv("SUB_DEAD_LETTER_QUEUE_URL")
LONG_POLL_TIME = os.getenv("LONG_POLL_TIME")
SNS_NAME = os.getenv("SNS_NAME")

def receive_message(sqs_client, queue_url):
    response = sqs_client.receive_message(
        QueueUrl=queue_url,
        MaxNumberOfMessages=1,
        # Long Polling
        WaitTimeSeconds=(int (LONG_POLL_TIME)))

    if len(response.get('Messages', [])) > 0:
        print(f"Number of messages received: {len(response.get('Messages', []))}")
        stdout.flush()
    return response

def delete_message(sqs_client, queue_url, receipt_handle):
    sqs_client.delete_message(QueueUrl=queue_url, ReceiptHandle=receipt_handle)

def delete_messages(sqs_client, queue_url, messages):
    for message in messages.get("Messages", []):
        receipt_handle = message['ReceiptHandle']
        delete_message(sqs_client=sqs_client, queue_url=queue_url, receipt_handle=receipt_handle)

def process_messages(topic, messages):
    for message in messages.get("Messages", []):
        sns_client.publish_message(topic, message)

def poll_queue(running):
    """ Poll the SQS queue and process messages. """
    while running.value:
        try:
             # Poll the SQS
             messages = receive_message(sqs_client=sqs_client, queue_url=QUEUE_URL)

             if messages:
                 process_messages(topic=topic, messages=messages)
                 delete_messages(sqs_client=sqs_client, queue_url=QUEUE_URL, messages=messages)

             dl_messages = receive_message(sqs_client=sqs_client, queue_url=DEAD_LETTER_QUEUE_URL)
             if dl_messages:
                 process_messages(topic=topic, messages=dl_messages)
                 delete_messages(sqs_client=sqs_client, queue_url=DEAD_LETTER_QUEUE_URL, messages=dl_messages)

        except Exception as e:
             print(f"An error occurred receiving or deleting messages: {e}")
             stdout.flush()

app = Flask(__name__)
@app.route('/shutdown', methods=['POST'])
def shutdown():
    """ Gracefully shutdown the polling process."""

    #Set the shared variable to False, shutting down the process
    running.value = False
    return jsonify({'status': 'shutting down'})

sqs_client = boto3.client("sqs", region_name=AWS_REGION)
sns_resource = boto3.resource("sns")
sns_client = Sns(sns_resource)
topic = sns_client.create_topic(SNS_NAME)

#Shared boolean value for process communication
running = multiprocessing.Value('b',True)

if __name__ == "__main__":
    print("Starting to poll the SQS queue...")
    # Start the polling process
    poll_process = multiprocessing.Process(target=poll_queue, args=(running,))
    poll_process.start()

    # Start the Flask app in the main process
    # Expose the app on all interfaces
    app.run(host='0.0.0.0', port=5000)
    
    # Wait for the polling process to finish before exiting
    poll_process.join()
    print("Exited polling loop.")
