#!/bin/bash

source ./resources/scripts/shared.sh

if [[ -e "$CID_FILE_NEO4J" ]]; then
	docker stop `cat $CID_FILE_NEO4J`
	rm $CID_FILE_NEO4J
fi

docker run \
	-d \
	--net=host \
	--cidfile=$CID_FILE_NEO4J \
    --publish=7474:7474 \
    --publish=7687:7687 \
    --volume=`pwd`/data/neo4j:/data \
    "$IMAGE_NEO4J"
