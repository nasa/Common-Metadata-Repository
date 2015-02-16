#!/bin/sh

# Runs clean and install on all dependencies.
# Must be run from within the cmr-dev-system folder


cd ../common-lib
lein do clean, install, clean

cd ../vdd-spatial-viz
lein do clean, install, clean
# Run as a separate step since it fails in CI but works locally. It's not necessary for CI.
lein compile-coffeescript

cd ../message-queue-lib
lein do clean, install, clean

cd ../spatial-lib
lein do clean, install, clean

cd ../umm-lib
lein do clean, install, clean

cd ../system-trace-lib
lein do clean, install, clean

cd ../oracle-lib
lein do clean, install, clean

cd ../elastic-utils-lib
lein do clean, install, clean

cd ../transmit-lib
lein do clean, install, clean

cd ../acl-lib
lein do clean, install, clean

cd ../es-spatial-plugin
lein do clean, install, clean

cd ../mock-echo-app
lein do clean, install, clean

cd ../metadata-db-app
lein do clean, install, clean

cd ../indexer-app
lein do clean, install, clean

cd ../index-set-app
lein do clean, install, clean

cd ../ingest-app
lein do clean, install, clean

cd ../search-app

# Generate search docs unless skip-docs is passed in as an argument.
if [ "$1" != "skip-docs" ]
  then
  lein generate-docs
fi

lein do clean, install, clean

cd ../bootstrap-app
lein do clean, install, clean

cd ../system-int-test
lein do clean, install, clean

cd ../dev-system
lein clean