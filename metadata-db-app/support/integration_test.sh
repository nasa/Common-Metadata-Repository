#!/bin/sh

# This script installs the dependent libraries, builds the uberjar, runs it and runs the integration tests.

echo "Installing dependent libraries"

## Install CMR common
cd ../common-lib
lein install

## Install System trace lib
cd ../system-trace-lib
lein install

## Build metadata db uber jar
cd ../metadata-db-app
lein uberjar

## Capture the uberjar filename
UBERJAR=`find target/*standalone.jar`
nohup java -jar $UBERJAR &
MDB_PID=$!

echo "Metadata DB started with process id: " $MDB_PID

lein with-profile +integration-test test-out

echo "Kill metadata db"
kill $MDB_PID

