#!/bin/sh
# This script is used to manage building and testing the CMR applications within the continuous
# integration (CI) environment. The intent is to never need to modify the configuration of the CI
# server. Instead the CI server will simply call this script. The script should be run from the cmr
# root directory, ie, ./dev-system/support/build-and-test-ci.sh

lein modules do clean, install
cd search-app
lein with-profile docs generate-docs
cd ../dev-system
./support/build-and-run.sh
cd ..
CMR_ELASTIC_PORT=9206 lein modules test-out
curl -XPOST http://localhost:2999/stop; true