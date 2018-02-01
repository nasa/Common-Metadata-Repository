#!/bin/sh
IFS='' read -r -d '' CLEAN_HELP <<'EOH'

Usage: cmr clean [SUBCOMMANDS]

With no subcommand, will perform the 'cmr clean all' operation.

Defined subcommands:

	PROJ 		   - Perform 'clean' operation on the given PROJ prpoject.
	all            - Perform 'clean' operation on all projects.
	es-data		   - Clean the Elasticsearch data directory.
	help           - Show this message.

EOH

function clean_all () {
	echo "Cleaning all projects ..."
	cd $CMR_DIR && lein clean-all
}

function clean_es_data () {
	if [ -d es_data ] ; then
		echo "Cleaning Elasticsearch data ..."
		rm -rf es_data
	fi
}

function clean_proj () {
	PROJ=$1
	echo "Cleaning  project '$PROJ' ..."
	cd $CMR_DIR/$PROJ && lein clean
}