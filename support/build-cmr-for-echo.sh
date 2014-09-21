#!/bin/sh

# A temporary helper script for building a CMR instance for ECHO

support/clean-and-install-dependencies.sh

# Generate search docs
cd ../cmr-search-app
lein generate-docs
cd ../cmr-dev-system

LEIN_SNAPSHOTS_IN_RELEASE=true lein uberjar
scp target/cmr-dev-system-*-standalone.jar cmr-wl-app1.dev.echo.nasa.gov:
lein clean