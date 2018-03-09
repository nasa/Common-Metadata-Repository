#!/bin/bash

source ./resources/scripts/shared.sh

if [[ -e "$CID_FILE_ES" ]]; then
	docker stop `cat $CID_FILE_ES`
	rm $CID_FILE_ES
fi

docker pull "$IMAGE_ES"
docker run \
    -d \
	--net=host \
	--cidfile=$CID_FILE_ES \
    --publish=${POST_ES}:9200 \
    --publish=9311:9300 \
    --volume=`pwd`/data/elastic:/usr/share/elasticsearch/data \
    --volume=`pwd`/logs/elastic:/usr/share/elasticsearch/logs \
    -e "discovery.type=single-node" \
    -e "node.name=${HOST_ES}" \
    "$IMAGE_ES"
