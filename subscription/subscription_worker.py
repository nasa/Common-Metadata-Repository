import boto3
import json
import multiprocessing
import os
import time
from flask import Flask, jsonify
from sys import stdout
from botocore.exceptions import ClientError

AWS_REGION = os.getenv("AWS_REGION")
QUEUE_URL = os.getenv("QUEUE_URL")
DEAD_LETTER_QUEUE_URL = os.getenv("DEAD_LETTER_QUEUE_URL")
TIME_TO_NEXT_POLL = os.getenv("TIME_TO_NEXT_POLL")
LONG_POLL_TIME = os.getenv("LONG_POLL_TIME")
SNS_NAME = os.getenv("SNS_NAME")

class sns:
    """Encapsulates AWS SNS topics."""

    def __init__(self, sns_resource):
        """:param sns_resource: A Boto3 AWS SNS resource."""
        self.sns_resource = sns_resource

    def create_topic(self, topic_name):
        """The topic is already created, but this is best practice to get the topic client
        from AWS."""
        try:
            topic = self.sns_resource.create_topic(Name=topic_name)
        except ClientError as error:
            print("Count not get the topic ARN: {error}.")
            raise
        else:
            return topic

    @staticmethod
    def publish_message(topic, message):
        """ Publishes a message with attributes to the CMR external topic. Subscriptions
        can be filtered based on the message attributes. """
        print(f"In publish_message - message is: {message} {type(message)}")
        stdout.flush()
        message_body_str = message["Body"]
        print(f"message_body: {message_body_str} {type(message_body_str)}")
        stdout.flush()
        message_body = json.loads(message_body_str)
        print("converted str to json")
        stdout.flush()
        message_subject = message_body["Subject"]
        print(f"message_subject: {message_subject} {type(message_subject)}")
        stdout.flush()
        message_attributes = message_body["MessageAttributes"]
        print(f"message_attributes: {message_attributes} {type(message_attributes)}")
        stdout.flush()
        message_message = message_body["Message"]
        try:
            if message_attributes:
                print(f"In if of message_attributes: {message_attributes} {type(message_attributes)}")
                stdout.flush()
                att_dict = {}
                for key in message_attributes.keys():
                    print(f"in for with key: {key} {message_attributes[key]['Value']}")
                    stdout.flush()
                    att_dict[key] = {"DataType": "String", "StringValue": message_attributes[key]["Value"]}

                print(f"Publishing message with attributes {att_dict} to topic {topic}.")
                stdout.flush()
                response = topic.publish(Subject=message_subject, Message=message_message, MessageAttributes=att_dict)
                print(f"Published message with attributes {att_dict} to topic {topic}.")
                stdout.flush()
            else:    
                response = topic.publish(Subject=message_subject, Message=message_message)
            print(f"Published message with attributes {message_attributes} to topic {topic}.")
            stdout.flush()
        except ClientError as error:
            print(f"Could not publish message to topic {topic}. {error}")
            stdout.flush()
            raise error
        else:
            return response

def receive_message(sqs_client, queue_url):
    response = sqs_client.receive_message(
        QueueUrl=queue_url,
        MaxNumberOfMessages=1,
        # Long Polling
        WaitTimeSeconds=(int (LONG_POLL_TIME))
    )

    print(f"Number of messages received: {len(response.get('Messages', []))}")
    stdout.flush()

    for message in response.get("Messages", []):
        message_body = message["Body"]
        print (f"Message body -no json {message_body}")
        stdout.flush()
        print(f"Message body - json: {json.loads(message_body)}")
        stdout.flush()
        print(f"Receipt Handle: {message['ReceiptHandle']}")
        stdout.flush()
    return response

def delete_message(sqs_client, queue_url, receipt_handle):
    response = sqs_client.delete_message(
        QueueUrl=queue_url,
        ReceiptHandle=receipt_handle
    )
    print(response)
    stdout.flush()

def delete_messages(sqs_client, queue_url, messages):
    for message in messages.get("Messages", []):
        receipt_handle = message['ReceiptHandle']
        delete_message(sqs_client=sqs_client, queue_url=queue_url, receipt_handle=receipt_handle)

def check_acls(message):
    return True

def process_messages(topic, messages):
    for message in messages.get("Messages", []):
        if check_acls(message=message):
            print("publishing a message")
            stdout.flush()
            sns_client.publish_message(topic, message)
        

def poll_queue(running):
    """ Poll the SQS queue and process messages. """
    while running.value:
        try:
             # Poll the SQS
             print(f"Polling the {QUEUE_URL} SQS Queue.")
             stdout.flush()
             messages = receive_message(sqs_client=sqs_client, queue_url=QUEUE_URL)

             if messages:
                 process_messages(topic=topic, messages=messages)
                 delete_messages(sqs_client=sqs_client, queue_url=QUEUE_URL, messages=messages)

             dl_messages = receive_message(sqs_client=sqs_client, queue_url=DEAD_LETTER_QUEUE_URL)
             if dl_messages:
                 process_messages(topic=topic, messages=dl_messages)
                 delete_messages(sqs_client=sqs_client, queue_url=DEAD_LETTER_QUEUE_URL, messages=dl_messages)
          
             time.sleep(int(TIME_TO_NEXT_POLL))
        except Exception as e:
             print(f"An error occurred receiving or deleting messages: {e}")
             stdout.flush()
             time.sleep(int(TIME_TO_NEXT_POLL))

app = Flask(__name__)
@app.route('/shutdown', methods=['POST'])
def shutdown():
    """ Gracefully shutdown the polling process."""

    #Set the shared variable to False, shutting down the process
    running.value = False
    return jsonify({'status': 'shutting down'})


#session = boto3.Session(profile_name='cmr-sit')
sqs_client = boto3.client("sqs", region_name=AWS_REGION)
sns_resource = boto3.resource("sns")
#sns_client = boto3.client("sns", region_name=AWS_REGION)
sns_client = sns(sns_resource)
topic = sns_client.create_topic(SNS_NAME)
print(f"The topic is: {topic}")

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

