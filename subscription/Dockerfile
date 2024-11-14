# Use python image
FROM python:3.13-slim
ARG AWS_REGION
ARG QUEUE_URL
ARG DEAD_LETTER_QUEUE_URL
ARG SNS_NAME
ARG SUB_DEAD_LETTER_QUEUE_URL

#Set environment variables
ENV AWS_REGION=$AWS_REGION
ENV QUEUE_URL=$QUEUE_URL
ENV DEAD_LETTER_QUEUE_URL=$DEAD_LETTER_QUEUE_URL
ENV LONG_POLL_TIME 10
ENV SNS_NAME=$SNS_NAME
ENV SUB_DEAD_LETTER_QUEUE_URL=$SUB_DEAD_LETTER_QUEUE_URL

#Set working directory
WORKDIR /app

#Copy the application files
COPY *.py .

#Install the required packages
RUN pip install boto3 Flask

#EXPOSE 8089
# Command to run the application
CMD ["python", "subscription_worker.py"]

