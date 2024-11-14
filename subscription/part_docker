# Use python image
FROM python:3.13-slim

#Set environment variables
#ENV AWS_REGION us-east-1
#ENV QUEUE_URL https://sqs.us-east-1.amazonaws.com/832706493240/cmr-internal-subscriptions-queue-sit
#ENV DEAD_LETTER_QUEUE_URL https://sqs.us-east-1.amazonaws.com/832706493240/cmr-internal-subscriptions-dead-letter-queue-sit
#ENV TIME_TO_NEXT_POLL 10
#ENV LONG_POLL_TIME 10
#ENV SNS_NAME cmr-subscriptions-sit

#Set working directory
WORKDIR /app

#Copy the application files
COPY *.py .

#Install the required packages
RUN pip install boto3 Flask

#EXPOSE 8089
# Command to run the application
CMD ["python", "subscription_worker.py"]

