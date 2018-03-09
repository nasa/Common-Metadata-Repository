#!/bin/bash

source ./resources/scripts/shared.sh

docker network create $DOCKER_NET
docker network connect $DOCKER_NET `cat $CID_FILE_NEO4J`
docker network connect $DOCKER_NET `cat $CID_FILE_ES`
docker network connect $DOCKER_NET `cat $CID_FILE_KIBANA`
