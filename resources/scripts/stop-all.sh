#!/bin/bash

source ./resources/scripts/shared.sh

docker stop `cat $CID_FILE_KIBANA`
docker stop `cat $CID_FILE_ES`
docker stop `cat $CID_FILE_NEO4J`
docker network rm $DOCKER_NET
