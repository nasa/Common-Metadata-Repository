#!/bin/sh
# This script is used to manage building and testing the CMR applications within the continuous
# integration (CI) environment connecting to an external Oracle database server. The intent is to
# never need to modify the configuration of the CI server. Instead the CI server will simply call
# this script. The script should be run from the cmr root directory, ie,
# ./dev-system/support/build-and-test-ci-with-oracle.sh

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
# Setup the database for ingest, bootstrap, and metadata-db

date && echo "Creating database users" &&

# Should be able to use lein modules, however it is reporting that create-user is returning a
# non-zero exit code even though manually running lein create-user for an app returns 0 exit code
# lein modules :dirs "ingest-app:bootstrap-app:metadata-db-app" create-user

for i in metadata-db-app bootstrap-app ingest-app; do
  (cd $i && lein create-user)
  if [ $? -ne 0 ] ; then
    echo "Failed to create database users for $i" >&2
    exit 1
  fi
done

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
