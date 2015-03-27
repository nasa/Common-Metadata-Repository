#!/bin/sh
# This script is used to manage building and testing the CMR applications within the continuous
# integration (CI) environment. The intent is to never need to modify the configuration of the CI
# server. Instead the CI server will simply call this script. The script should be run from the cmr
# root directory, ie, ./dev-system/support/build-and-test-ci.sh

date && echo "Installing all apps" &&
lein modules do clean, install &&
date && echo "Generating search API documentation" &&
(cd search-app && lein with-profile docs generate-docs) &&
if [ "$1" != "skip-uberjars" ]
  then
  date && echo "Building uberjars" &&
  lein with-profile uberjar modules uberjar
fi
date && echo "Building and starting dev-system" &&
(cd dev-system && support/build-and-run.sh) &&
date && echo "Running tests" &&
CMR_ELASTIC_PORT=9206 lein modules test-out &&
date && echo "Stopping applications" &&
curl -XPOST http://localhost:2999/stop; true