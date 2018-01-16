#!/bin/sh
# This script is used to manage building and testing the CMR applications within the continuous
# integration (CI) environment. The intent is to never need to modify the configuration of the CI
# server. Instead the CI server will simply call this script. The script should be run from the cmr
# root directory, ie, ./dev-system/support/build-and-test-ci.sh
#
# The script uses two environment variables:
# CMR_BUILD_UBERJARS: If set to true, the script will create the application uberjars.
# CMR_DEV_SYSTEM_DB_TYPE: If set to external, the script will create all of the users required and
# run the database migrations to setup an Oracle database for use with CMR. Note that the caller
# should also set CMR_DB_URL to the URL to connect to the external database.

date && echo "Building and starting dev-system" &&
(cd dev-system && support/build-and-run-internal.sh)
if [ $? -ne 0 ] ; then
  echo "Failed to build and start up dev system" >&2
  exit 1
fi
date && echo "Running tests" &&
lein test
if [ $? -ne 0 ] ; then
  echo "Failed Tests" >&2
  cat */testreports.xml */test2junit/xml/*.xml
  (curl -XPOST http://localhost:2999/stop; true)
  exit 1
fi
cat */testreports.xml */test2junit/xml/*.xml
date && echo "Stopping applications" &&
(curl -XPOST http://localhost:2999/stop; true)
