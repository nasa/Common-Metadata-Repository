import boto3
import json
from botocore.exceptions import ClientError
from logger import logger

class Sns:
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
            logger.error("Subscription Worker could not get the topic ARN: {error}.")
            raise error
        else:
            return topic

    @staticmethod
    def publish_message(topic, message):
        """ Publishes a message with attributes to the CMR external topic. Subscriptions
        can be filtered based on the message attributes. """
        message_body_str = message["Body"]
        message_body = json.loads(message_body_str)
        message_subject = message_body["Subject"]
        message_attributes = message_body["MessageAttributes"]
        message_message = message_body["Message"]
        try:
            if message_attributes:
                att_dict = {}
                for key in message_attributes.keys():
                    att_dict[key] = {"DataType": "String", "StringValue": message_attributes[key]["Value"]}
                response = topic.publish(Subject=message_subject, Message=message_message, MessageAttributes=att_dict)
            else:    
                response = topic.publish(Subject=message_subject, Message=message_message)
        except ClientError as error:
            logger.error(f"Subscription Worker could not publish message to topic {topic}. {error}")
            raise error
        else:
            return response
