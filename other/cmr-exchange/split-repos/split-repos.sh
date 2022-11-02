#!/bin/bash

START=`date`
SCRIPT_DIR=`dirname $0`

if [[ ! -d "cmr-preped" ]]; then
	$SCRIPT_DIR/prep-repos.sh
fi

PROJS="orbits-lib mock-echo-app oracle-lib elastic-utils-lib common-app-lib metadata-db-app indexer-app search-relevancy-test virtual-product-app message-queue-lib access-control-app ingest-app acl-lib system-int-test es-spatial-plugin bootstrap-app search-app spatial-lib umm-spec-lib common-lib vdd-spatial-viz transmit-lib umm-lib dev-system"
if [[ ! -d "cli" ]]; then
	./extract-cli.sh -y
fi

for PROJ in $PROJS; do
	if [[ ! -d "$PROJ" ]]; then
		 $SCRIPT_DIR/extract-project.sh $PROJ -y
	else
		echo "Directory '$PROJ' exists; skipping ..."
	fi
done

if [[ -d "cmr-nasa" ]]; then
	if [[ ! -d "cmr" ]]; then
		$SCRIPT_DIR/create-cmr-umbrella/submodules.sh -y
	fi
else
	echo "The expected repo 'cmr-nasa' doesn't exist; exiting ..."
	exit 127
fi

END=`date`

echo "Extraction started: $START"
echo "Extraction finished: $END"
