#!/bin/sh

# Builds the uberjars for all the applications. It's expected that this script will be executed from
# the cmr-dev-system folder.

echo "Building all dependencies"
support/clean-and-install-dependencies.sh

echo "Building application uberjars"
cd ../metadata-db-app
lein uberjar
cd ../indexer-app
lein uberjar
cd ../index-set-app
lein uberjar
cd ../ingest-app
lein uberjar
cd ../search-app
lein uberjar
cd ../bootstrap-app
lein uberjar

cd ../dev-system
mkdir uberjars
echo "Copying all uberjars to cmr-dev-system/uberjars"
cp ../*-app/target/*-standalone.jar uberjars/

echo "Cleaning application folders"
cd ../metadata-db-app
lein clean
cd ../indexer-app
lein clean
cd ../index-set-app
lein clean
cd ../ingest-app
lein clean
cd ../search-app
lein clean
cd ../bootstrap-app
lein clean

echo "done"