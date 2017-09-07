#! /bin/bash

DOCKER_REPO=pafortin/goaws
CONTAINER_ID_FILE=/tmp/cmr-sqs-sns-docker-cid

echo "Starting local SQS/SNS services with docker ..."
rm -f $CONTAINER_ID_FILE
docker pull $DOCKER_REPO
docker run -d --cidfile=$CONTAINER_ID_FILE -p 4100:4100 $DOCKER_REPO
echo "Done."
