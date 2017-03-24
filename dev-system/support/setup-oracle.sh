#!/bin/sh
# This script is used to setup an Oracle database for use with the CMR. It creates all of the users
# and runs the database migrations to fully setup Oracle. The script should be run from the cmr root
# directory, ie, # ./dev-system/support/setup-oracle.sh

# Should be able to use lein modules, however it is reporting that create-user is returning a
# non-zero exit code even though manually running lein create-user for an app returns 0 exit code
# lein modules :dirs "ingest-app:bootstrap-app:metadata-db-app" create-user

# Fail the script if the required environment variables are not set.
if [ -z "${CMR_METADATA_DB_PASSWORD}" ] || 
   [ -z "${CMR_BOOTSTRAP_PASSWORD}" ] || 
   [ -z "${CMR_INGEST_PASSWORD}" ]; then
  echo "Failed running the script because one or more of the following environment variables are not set: CMR_METADATA_DB_PASSWORD, CMR_BOOTSTRAP_PASSWORD and CMR_INGEST_PASSWORD" >&2
 exit 1
fi 

date && echo "Creating database users" &&
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
