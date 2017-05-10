#!/bin/sh
# This script is used to setup CMR for local development. It assumes you have
# cloned the whole repo, installed leiningen and Java.
#
# Additionally, this script assumes it is being executed in the parent
# directory of the the dev-system project.

date && echo "Installing all apps and generating API documentation" &&
lein install-with-content!
if [ $? -ne 0 ] ; then
  echo "Failed to install apps and generate docs" >&2
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

