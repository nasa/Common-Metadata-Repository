#!/bin/sh
# This script is used to manage building the CMR applications
#
# The script uses these environment variables:
# CMR_BUILD_UBERJARS: If set to true, the script will create the application uberjars.
# CMR_DEV_SYSTEM_DB_TYPE: If set to external, the script will create all of the users required and
# run the database migrations to setup an Oracle database for use with CMR. Note that the caller
# should also set CMR_DB_URL to the URL to connect to the external database.
# CMR_ORACLE_JAR_REPO: Oracle libraries are not available in public maven repositories. We can host
# them in internal ones for building. If this is set then the maven repo will be updated to use this.

date && echo "Installing all apps" &&
(cd .. && lein modules do clean, install)
if [ $? -ne 0 ] ; then
  echo "Failed to install apps" >&2
  exit 1
fi
# The library is reinstalled after installing gems so that it will contain the gem code.
date && echo "Installing collection renderer gems and reinstalling library" &&
(cd ../collection-renderer-lib && lein do install-gems, install, clean)
if [ $? -ne 0 ] ; then
  echo "Failed to install gems" >&2
  exit 1
fi
# Orbit gems are only a test time dependency so the library doesn't need to be reinstalled.
date && echo "Installing orbits gems" &&
(cd ../orbits-lib && lein install-gems)
if [ $? -ne 0 ] ; then
  echo "Failed to install gems" >&2
  exit 1
fi
(cd ../search-app && lein generate-docs)
if [ $? -ne 0 ] ; then
  echo "Failed to generate search docs" >&2
  exit 1
fi
date && echo "Generating Ingest API documentation" &&
(cd ../ingest-app && lein with-profile docs generate-docs)
if [ $? -ne 0 ] ; then
  echo "Failed to generate ingest docs" >&2
  exit 1
fi
date && echo "Generating Access Control API documentation" &&
(cd ../access-control-app && lein with-profile docs generate-docs)
if [ $? -ne 0 ] ; then
  echo "Failed to generate access control docs" >&2
  exit 1
fi
if [ "$CMR_DEV_SYSTEM_DB_TYPE" = "external" ] ; then
  (cd ../ && dev-system/support/setup-oracle.sh)
  if [$? -ne 0 ] ; then
    echo "Failed to setup Oracle" >&2
    exit 1
  fi
fi
if [ "$CMR_BUILD_UBERJARS" = "true" ] ; then
  date && echo "Building uberjars" &&
  (cd .. && lein with-profile uberjar modules uberjar)
  if [ $? -ne 0 ] ; then
    echo "Failed to generate uberjars" >&2
    exit 1
  fi
fi

date && echo "Building dev system uberjar" &&
lein do clean, uberjar
if [ $? -ne 0 ] ; then
  echo "Failed to generate dev system uberjar" >&2
  exit 1
fi
