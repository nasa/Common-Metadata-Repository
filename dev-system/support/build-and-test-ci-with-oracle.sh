#!/bin/sh
# This script is used to manage building and testing the CMR applications within the continuous
# integration (CI) environment. The intent is to never need to modify the configuration of the CI
# server. Instead the CI server will simply call this script. The script should be run from the cmr
# root directory, ie, ./dev-system/support/build-and-test-ci.sh
# There is one optional parameter 'skip-uberjars'. The script will not build the uberjars for the
# CMR applications when this parameter is passed.

date && echo "Installing all apps" &&
lein modules do clean, install
if [ $? -ne 0 ] ; then
  echo "Failed to install apps" >&2
  exit 1
fi
date && echo "Generating Search API documentation" &&
(cd search-app && lein with-profile docs generate-docs)
if [ $? -ne 0 ] ; then
  echo "Failed to generate search docs" >&2
  exit 1
fi
date && echo "Generating Ingest API documentation" &&
(cd ingest-app && lein with-profile docs generate-docs)
if [ $? -ne 0 ] ; then
  echo "Failed to generate ingest docs" >&2
  exit 1
fi

##############################################################################
# Build uberjars and setup the database for ingest, bootstrap, and metadata-db
date && echo "Building uberjars" &&
lein modules :dirs "ingest-app:bootstrap-app:metadata-db-app" uberjar
if [ $? -ne 0 ] ; then
  echo "Failed to generate uberjars" >&2
  exit 1
fi

date && echo "Creating database users" &&
lein modules :dirs "ingest-app:bootstrap-app:metadata-db-app" create-user
if [ $? -ne 0 ] ; then
  echo "Failed to create database users" >&2
  exit 1
fi

date && echo "Running database migrations" &&
lein modules :dirs "ingest-app:bootstrap-app:metadata-db-app" migrate
if [ $? -ne 0 ] ; then
  echo "Failed to run database migrations" >&2
  exit 1
fi
##############################################################################

date && echo "Building and starting dev-system using Oracle" &&
(cd dev-system && CMR_DEV_SYSTEM_DB_TYPE=external support/build-and-run.sh)
if [ $? -ne 0 ] ; then
  echo "Failed to build and start up dev system using Oracle" >&2
  exit 1
fi
date && echo "Running tests" &&
CMR_ELASTIC_PORT=9206 lein modules test-out
if [ $? -ne 0 ] ; then
  echo "Failed Tests" >&2
  cat */testreports.xml
  (curl -XPOST http://localhost:2999/stop; true)
  exit 1
fi
cat */testreports.xml
date && echo "Stopping applications" &&
(curl -XPOST http://localhost:2999/stop; true)
