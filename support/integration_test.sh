#!/bin/sh

# This script installs the dependent libraries, builds the uberjar, runs it and runs the integration tests.

## Install CMR common
cd ../cmr-common-lib
lein install

## Install System trace lib
cd ../cmr-system-trace-lib
lein install

## Build metadata db uber jar
cd ../cmr-metadata-db-app
lein uberjar

## Capture the uberjar filename
UBERJAR=`find target/*standalone.jar`
nohup java -jar $UBERJAR &
MDB_PID=$!

echo "Metadata DB started with process id: " $MDB_PID

lein with-profile +integration-test test-out

echo "Kill metadata db"
kill $MDB_PID

