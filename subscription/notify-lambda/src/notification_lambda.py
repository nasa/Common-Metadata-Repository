import json
import requests
from logger import logger

# This lambda is triggered through a subscription to the cmr-internal-subscription-sit SNS topic. It processes the events which are notifications that get sent
# to an external URL.

# Lambda's cannot handle Type hints in some cases. They are left as comments.
# handler(event: Dict[str, Any], context: Any) -> None:
def handler(event, context):
    """The handler is the starting point that is triggered by an SNS topic subscription with a filter that designates tha the notification sent is a URL notification. """

    logger.debug(f"Ingest notification lambda received event: {json.dumps(event, indent=2)}")
    for record in event['Records']:
        process_message(record)

# process_message(record: Dict[str, Any]) ->None:
def process_message(record):
    """Processes the record in the event. """

    try:
        logger.info(f"Ingest notification lambda processing message - record: {record}")
        message = record['Sns']
        message_attributes = record['Sns']['MessageAttributes']
        url = message_attributes['endpoint']['Value']
        send_message(url, message)
        
    except Exception as e:
        logger.error(f"Ingest notification lambda an error occurred {e} while trying to send the record: {record}")
        raise e

# send_message(url: str, message: Dict[str, Any]) -> None:
def send_message(url, message):
    """Sends the passed message to the external URL. If not successful the message is put onto a dead letter queue."""
    # Prepare the data to be sent

    try:
        # Send a POST request to the URL with the message data
        headers = {'Content-Type': 'application/json'}
        logger.info(f"Ingest notification lambda sending message ID: {message['MessageId']} to URL: {url}")
        response = requests.post(url, headers=headers, json=message)

        # Check if the request was successful
        if response.status_code == 200:
            logger.info(f"Ingest notification lambda successfully sent message ID: {message['MessageId']}")
        else:
            logger.error(f"Ingest notification lambda failed to send message ID: {message['MessageId']}. Status code: {response.status_code}. Response: {response.text}")

    except requests.exceptions.RequestException as e:
        logger.error(f"Ingest notification lambda an error occurred while sending the message id {message['MessageId']} to URL: {url} {e}")

