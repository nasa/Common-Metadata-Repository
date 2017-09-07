#! /bin/bash

CONTAINER_ID_FILE=/tmp/cmr-sqs-sns-docker-cid

echo "Stopping local SQS/SNS services ..."
docker stop `cat $CONTAINER_ID_FILE`
echo "Done."
