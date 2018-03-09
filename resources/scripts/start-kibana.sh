#!/bin/bash

source ./resources/scripts/shared.sh

if [[ -e "$CID_FILE_KIBANA" ]]; then
	docker stop `cat $CID_FILE_KIBANA`
	rm $CID_FILE_KIBANA
fi

docker pull "$IMAGE_KIBANA"
docker run \
	-d \
	--net=host \
	--cidfile=$CID_FILE_KIBANA \
	--publish=5601:5601 \
    --volume=`pwd`/data/kibana:/usr/share/kibana/data \
    --volume=`pwd`/logs/kibana:/usr/share/kibana/logs \
    -e "server.name=$HOST_KIBANA" \
    -e "elasticsearch.url=http://localhost:${PORT_ES}" \
    -e "logging.dest=/usr/share/kibana/logs/kibana.log" \
    "$IMAGE_KIBANA"
