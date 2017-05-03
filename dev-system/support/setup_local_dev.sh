#!/bin/sh
# This script is used to setup CMR for local development. It assumes you have
# cloned the whole repo, installed leiningen and Java.
#
# Additionally, this script assumes it is being executed in the parent
# directory of the the dev-system project.

date && echo "Installing all apps" &&
lein modules do clean, install, clean
if [ $? -ne 0 ] ; then
  echo "Failed to install apps" >&2
  exit 1
fi
rm -r dev-system/checkouts
date && echo "Creating dev-system checkouts directory" &&
(cd dev-system && lein create-checkouts)
if [ $? -ne 0 ] ; then
  echo "Failed to create checkouts directory" >&2
  exit 1
fi
date && echo "Installing collection renderer gems" &&
(cd collection-renderer-lib && lein install-gems)
if [ $? -ne 0 ] ; then
  echo "Failed to install gems" >&2
  exit 1
fi
date && echo "Installing orbit library gems" &&
(cd orbits-lib && lein install-gems)
if [ $? -ne 0 ] ; then
  echo "Failed to install gems" >&2
  exit 1
fi
date && echo "Generating Search API documentation" &&
(cd search-app && lein generate-static)
if [ $? -ne 0 ] ; then
  echo "Failed to generate search docs" >&2
  exit 1
fi
date && echo "Generating Ingest API documentation" &&
(cd ingest-app && lein generate-static)
if [ $? -ne 0 ] ; then
  echo "Failed to generate ingest docs" >&2
  exit 1
fi
date && echo "Generating Access Control API documentation" &&
(cd access-control-app && lein generate-static)
if [ $? -ne 0 ] ; then
  echo "Failed to generate access control docs" >&2
  exit 1
fi


