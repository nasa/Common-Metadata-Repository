#!/bin/sh

chmod +x support/clean-and-install-dependencies.sh
chmod +x support/build-and-run.sh
chmod +x support/stop.sh
# We skip generating documentation here to make it faster. This will be tested as a port of testing cmr-search-app
./support/clean-and-install-dependencies.sh skip-docs
./support/stop.sh
./support/build-and-run.sh

cd ../system-int-test
CMR_ELASTIC_PORT=9206 lein test-out

cd ../dev-system
./support/stop.sh

lein clean