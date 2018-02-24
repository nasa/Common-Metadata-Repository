#!/bin/bash

START=`date`
PROJS="collection-renderer-lib orbits-lib index-set-app mock-echo-app cubby-app oracle-lib elastic-utils-lib common-app-lib metadata-db-app indexer-app search-relevancy-test virtual-product-app message-queue-lib access-control-app ingest-app acl-lib system-int-test es-spatial-plugin bootstrap-app search-app spatial-lib umm-spec-lib common-lib vdd-spatial-viz transmit-lib umm-lib dev-system"
if [[ ! -d "cli" ]]; then
	./extract-cli.sh -y
fi
for PROJ in $PROJS;
do
	if [[ ! -d "$PROJ" ]]; then
		 ./extract-project.sh $PROJ -y
	fi
done

if [[ ! -d "cmr" ]]; then
	./create-cmr-umbrella.sh
fi
END=`date`
echo "Extraction started: $START"
echo "Extraction finished: $END"
