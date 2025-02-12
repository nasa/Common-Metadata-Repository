import json
import requests
from logger import logger

def handler(event: Dict[str, Any], context: Any) -> None:
    logger.debug(f""Received event: {json.dumps(event, indent=2)}"
    for record in event['Records']:
        process_message(record)

def process_message(record: Dict[str, Any]) ->None:
    try:
        message = record['Sns']['Message']
        message_attributes = record['Sns']['MessageAttributes']
        logger.info(f"Processing message {message}")
        send_message(message_attributes['endpoint'], message)
        
    except Exception as e:
        logger.error(f"An error occurred {e}")
        raise e

def send_message(url: str, message: Dict[str, Any]) -> None:
    # Prepare the data to be sent

    try:
        # Send a POST request to the URL with the message data
        headers = {'Content-Type': 'application/json'}
        logger.debug("URL: ", url)
        logger.debug("Message: ", message)
        response = requests.post(url, headers=headers, json=message)

        # Check if the request was successful
        if response.status_code == 200:
            logger.debug("Message sent successfully!")
            logger.debug(f"Response: {response.text}")
        else:
            logger.error(f"Failed to send message. Status code: {response.status_code}")
            logger.debug(f"Response: {response.text}")

    except requests.exceptions.RequestException as e:
        logger.error(f"An error occurred while sending the message: {e}")

