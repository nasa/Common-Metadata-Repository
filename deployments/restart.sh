#!/bin/sh

# A temporary helper script for doing deployments until Bamboo deployments are setup.

if [ -z "$1" ]
  then
    echo "Must supply the name of the environment to deploy to: wl, sit."
    exit 1
fi

cd ../cmr-metadata-db-app
cmr_deploy_restart $1
cd ../cmr-index-set-app
cmr_deploy_restart $1
cd ../cmr-indexer-app
cmr_deploy_restart $1
cd ../cmr-search-app
cmr_deploy_restart $1
cd ../cmr-ingest-app
cmr_deploy_restart $1
cd ../cmr-bootstrap-app
cmr_deploy_restart $1
