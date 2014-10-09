#!/bin/sh

# Builds the uberjars for all the applications. It's expected that this script will be executed from
# the cmr-dev-system folder.

echo "Building all dependencies"
support/clean-and-install-dependencies.sh

echo "Building application uberjars"
cd ../cmr-metadata-db-app
lein uberjar
cd ../cmr-indexer-app
lein uberjar
cd ../cmr-index-set-app
lein uberjar
cd ../cmr-ingest-app
lein uberjar
cd ../cmr-search-app
lein uberjar
cd ../cmr-bootstrap-app
lein uberjar

cd ../cmr-dev-system
mkdir uberjars
echo "Copying all uberjars to cmr-dev-system/uberjars"
cp ../*-app/target/*-standalone.jar uberjars/

echo "Cleaning application folders"
cd ../cmr-metadata-db-app
lein clean
cd ../cmr-indexer-app
lein clean
cd ../cmr-index-set-app
lein clean
cd ../cmr-ingest-app
lein clean
cd ../cmr-search-app
lein clean
cd ../cmr-bootstrap-app
lein clean

echo "done"